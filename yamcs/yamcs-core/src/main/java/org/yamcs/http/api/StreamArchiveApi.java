package org.yamcs.http.api;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.archive.ReplayOptions;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.MediaType;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractStreamArchiveApi;
import org.yamcs.protobuf.Archive.ExportParameterValuesRequest;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterGroupsRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.Archive.ParameterGroupInfo;
import org.yamcs.protobuf.Archive.StreamParameterValuesRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;
import com.google.protobuf.ByteString;

public class StreamArchiveApi extends AbstractStreamArchiveApi<Context> {

    @Override
    public void listParameterGroups(Context ctx, ListParameterGroupsRequest request,
            Observer<ParameterGroupInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ParameterGroupInfo.Builder responseb = ParameterGroupInfo.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(ParameterRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues("group");
        if (enumValues != null) {
            List<String> unsortedGroups = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                unsortedGroups.add(entry.getKey());
            }
            Collections.sort(unsortedGroups);
            responseb.addAllGroup(unsortedGroups);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listParameterHistory(Context ctx, ListParameterHistoryRequest request,
            Observer<ListParameterHistoryResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String pathName = request.getName();

        ParameterWithId p = MdbApi.verifyParameterWithId(ctx, mdb, pathName);

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean noRepeat = request.getNorepeat();
        boolean descending = !request.getOrder().equals("asc");

        long start = TimeEncoding.INVALID_INSTANT;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }

        long stop = TimeEncoding.INVALID_INSTANT;
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        ReplayOptions rr = toParameterReplayRequest(p.getId(), start, stop, descending);

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        ParameterReplayListener replayListener = new ParameterReplayListener(pos, limit) {
            @Override
            public void onParameterData(List<ParameterValueWithId> params) {
                for (ParameterValueWithId pvalid : params) {
                    resultb.addParameter(pvalid.toGbpParameterValue());
                }
            }

            @Override
            public void replayFailed(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void replayFinished() {
                observer.complete(resultb.build());
            }
        };
        replayListener.setNoRepeat(noRepeat);

        ReplayFactory.replay(instance, ctx.user, rr, replayListener);
    }

    @Override
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request,
            Observer<TimeSeries> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = MdbApi.verifyParameter(ctx, mdb, request.getName());

        ParameterType ptype = p.getParameterType();
        if ((ptype != null) && (!(ptype instanceof FloatParameterType) && !(ptype instanceof IntegerParameterType))) {
            throw new BadRequestException(
                    "Only integer or float parameters can be sampled. Got " + ptype.getTypeAsString());
        }

        long stop = TimeEncoding.getWallclockTime();
        long start = stop - (1000 * 60 * 60); // 1 hour

        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }
        ReplayOptions repl = ReplayOptions.getAfapReplay(start, stop);
        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        repl.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(id).build());

        int sampleCount = request.hasCount() ? request.getCount() : 500;
        boolean useRawValue = request.hasUseRawValue() && request.getUseRawValue();
        Downsampler sampler = new Downsampler(start, stop, sampleCount, useRawValue);

        ParameterReplayListener replayListener = new ParameterReplayListener() {
            @Override
            public void onParameterData(List<ParameterValueWithId> params) {
                for (ParameterValueWithId pvalid : params) {
                    sampler.process(pvalid.getParameterValue());
                }
            }

            @Override
            public void replayFinished() {
                TimeSeries.Builder series = TimeSeries.newBuilder();
                for (Sample s : sampler.collect()) {
                    series.addSample(toGPBSample(s));
                }
                observer.complete(series.build());
            }

            @Override
            public void replayFailed(Throwable t) {
                observer.completeExceptionally(t);
            }
        };

        ReplayFactory.replay(instance, ctx.user, repl, replayListener);
    }

    @Override
    public void streamParameterValues(Context ctx, StreamParameterValuesRequest request,
            Observer<ParameterData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        ReplayOptions repl = ReplayOptions.getAfapReplay();
        List<NamedObjectId> ids = new ArrayList<>();
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        if (request.hasStart()) {
            repl.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            repl.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        for (NamedObjectId id : request.getIdsList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }
            ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
            ids.add(id);
        }

        if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                    ids.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                }
            }
        }
        repl.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(ids).build());

        ParameterReplayListener replayListener = new ParameterReplayListener() {

            @Override
            protected void onParameterData(List<ParameterValueWithId> params) {
                ParameterData.Builder pd = ParameterData.newBuilder();
                for (ParameterValueWithId pvalid : params) {
                    ParameterValue pval = pvalid.toGbpParameterValue();
                    pd.addParameter(pval);
                }
                observer.next(pd.build());
            }

            @Override
            public void replayFinished() {
                observer.complete();
            }

            @Override
            public void replayFailed(Throwable t) {
                observer.completeExceptionally(t);
            }
        };
        observer.setCancelHandler(replayListener::requestReplayAbortion);

        ReplayFactory.replay(instance, ctx.user, repl, replayListener);
    }

    @Override
    public void exportParameterValues(Context ctx, ExportParameterValuesRequest request, Observer<HttpBody> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        ReplayOptions repl = ReplayOptions.getAfapReplay();

        List<NamedObjectId> ids = new ArrayList<>();
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String namespace = null;

        if (request.hasStart()) {
            repl.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            repl.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        for (String id : request.getParametersList()) {
            ParameterWithId paramWithId = MdbApi.verifyParameterWithId(ctx, mdb, id);
            ids.add(paramWithId.getId());
        }
        if (request.hasNamespace()) {
            namespace = request.getNamespace();
        }

        if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (!ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                    continue;
                }
                if (namespace != null) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        ids.add(NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build());
                    }
                } else {
                    ids.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                }
            }
        }
        repl.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(ids).build());

        String filename;
        String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        if (ids.size() == 1) {
            NamedObjectId id = ids.get(0);
            String parameterName = id.hasNamespace() ? id.getName() : id.getName().substring(1);
            filename = parameterName.replace('/', '_') + "_export_" + dateString + ".csv";
        } else {
            filename = "parameter_export_" + dateString + ".csv";
        }

        boolean addRaw = false;
        boolean addMonitoring = false;
        for (String extra : request.getExtraList()) {
            if (extra.equals("raw")) {
                addRaw = true;
            } else if (extra.equals("monitoring")) {
                addMonitoring = true;
            } else {
                throw new BadRequestException("Unexpected option for parameter 'extra': " + extra);
            }
        }
        CsvParameterStreamer l = new CsvParameterStreamer(
                observer, filename, ids, addRaw, addMonitoring);
        if (request.hasDelimiter()) {
            switch (request.getDelimiter()) {
            case "TAB":
                l.columnDelimiter = '\t';
                break;
            case "SEMICOLON":
                l.columnDelimiter = ';';
                break;
            case "COMMA":
                l.columnDelimiter = ',';
                break;
            default:
                throw new BadRequestException("Unexpected column delimiter");
            }
        }
        observer.setCancelHandler(l::requestReplayAbortion);
        ReplayFactory.replay(instance, ctx.user, repl, l);
    }

    private static ReplayOptions toParameterReplayRequest(NamedObjectId parameterId, long start, long stop,
            boolean descend) {

        ReplayOptions repl = ReplayOptions.getAfapReplay(start, stop);
        repl.setReverse(descend);
        repl.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(parameterId).build());
        return repl;
    }

    public static TimeSeries.Sample toGPBSample(Sample sample) {
        TimeSeries.Sample.Builder b = TimeSeries.Sample.newBuilder();
        b.setTime(TimeEncoding.toString(sample.t));
        b.setN(sample.n);

        if (sample.n > 0) {
            b.setAvg(sample.avg);
            b.setMin(sample.min);
            b.setMax(sample.max);
        }

        return b.build();
    }

    private static class CsvParameterStreamer extends ParameterReplayListener {

        Observer<HttpBody> observer;
        List<NamedObjectId> ids;
        boolean addRaw;
        boolean addMonitoring;
        int recordCount = 0;
        char columnDelimiter = '\t';

        CsvParameterStreamer(Observer<HttpBody> observer, String filename, List<NamedObjectId> ids,
                boolean addRaw, boolean addMonitoring) {
            this.observer = observer;
            this.ids = ids;
            this.addRaw = addRaw;
            this.addMonitoring = addMonitoring;

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename(filename)
                    .build();

            observer.next(metadata);
        }

        @Override
        protected void onParameterData(List<ParameterValueWithId> params) {

            ByteString.Output data = ByteString.newOutput();
            try (Writer writer = new OutputStreamWriter(data, StandardCharsets.UTF_8);
                    ParameterFormatter formatter = new ParameterFormatter(writer, ids, columnDelimiter)) {
                formatter.setWriteHeader(recordCount == 0);
                formatter.setPrintRaw(addRaw);
                formatter.setPrintMonitoring(addMonitoring);
                formatter.writeParameters(params);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            HttpBody body = HttpBody.newBuilder()
                    .setData(data.toByteString())
                    .build();
            observer.next(body);
            recordCount++;
        }

        @Override
        public void replayFailed(Throwable t) {
            observer.completeExceptionally(t);
        }

        @Override
        public void replayFinished() {
            observer.complete();
        }
    }
}
