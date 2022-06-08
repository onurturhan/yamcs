/**
 * This class file was automatically generated by jASN1 v1.11.2 (http://www.beanit.com)
 */

package ccsds.sle.transfer.service.fsp.outgoing.pdus;

import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.io.Serializable;
import com.beanit.jasn1.ber.*;
import com.beanit.jasn1.ber.types.*;
import com.beanit.jasn1.ber.types.string.*;

import ccsds.sle.transfer.service.bind.types.SleBindReturn;
import ccsds.sle.transfer.service.bind.types.SlePeerAbort;
import ccsds.sle.transfer.service.bind.types.SleUnbindReturn;
import ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import ccsds.sle.transfer.service.common.types.ConditionalTime;
import ccsds.sle.transfer.service.common.types.Credentials;
import ccsds.sle.transfer.service.common.types.IntUnsignedLong;
import ccsds.sle.transfer.service.common.types.InvokeId;
import ccsds.sle.transfer.service.common.types.Time;
import ccsds.sle.transfer.service.fsp.structures.BufferSize;
import ccsds.sle.transfer.service.fsp.structures.DiagnosticFspGet;
import ccsds.sle.transfer.service.fsp.structures.DiagnosticFspInvokeDirective;
import ccsds.sle.transfer.service.fsp.structures.DiagnosticFspStart;
import ccsds.sle.transfer.service.fsp.structures.DiagnosticFspThrowEvent;
import ccsds.sle.transfer.service.fsp.structures.DiagnosticFspTransferData;
import ccsds.sle.transfer.service.fsp.structures.FspGetParameter;
import ccsds.sle.transfer.service.fsp.structures.FspNotification;
import ccsds.sle.transfer.service.fsp.structures.FspPacketCount;
import ccsds.sle.transfer.service.fsp.structures.FspPacketLastOk;
import ccsds.sle.transfer.service.fsp.structures.FspPacketLastProcessed;
import ccsds.sle.transfer.service.fsp.structures.FspProductionStatus;
import ccsds.sle.transfer.service.fsp.structures.PacketIdentification;

public class FspGetParameterReturn implements BerType, Serializable {

	private static final long serialVersionUID = 1L;

	public static class Result implements BerType, Serializable {

		private static final long serialVersionUID = 1L;

		public byte[] code = null;
		private FspGetParameter positiveResult = null;
		private DiagnosticFspGet negativeResult = null;
		
		public Result() {
		}

		public Result(byte[] code) {
			this.code = code;
		}

		public void setPositiveResult(FspGetParameter positiveResult) {
			this.positiveResult = positiveResult;
		}

		public FspGetParameter getPositiveResult() {
			return positiveResult;
		}

		public void setNegativeResult(DiagnosticFspGet negativeResult) {
			this.negativeResult = negativeResult;
		}

		public DiagnosticFspGet getNegativeResult() {
			return negativeResult;
		}

		public int encode(OutputStream reverseOS) throws IOException {

			if (code != null) {
				for (int i = code.length - 1; i >= 0; i--) {
					reverseOS.write(code[i]);
				}
				return code.length;
			}

			int codeLength = 0;
			int sublength;

			if (negativeResult != null) {
				sublength = negativeResult.encode(reverseOS);
				codeLength += sublength;
				codeLength += BerLength.encodeLength(reverseOS, sublength);
				// write tag: CONTEXT_CLASS, CONSTRUCTED, 1
				reverseOS.write(0xA1);
				codeLength += 1;
				return codeLength;
			}
			
			if (positiveResult != null) {
				sublength = positiveResult.encode(reverseOS);
				codeLength += sublength;
				codeLength += BerLength.encodeLength(reverseOS, sublength);
				// write tag: CONTEXT_CLASS, CONSTRUCTED, 0
				reverseOS.write(0xA0);
				codeLength += 1;
				return codeLength;
			}
			
			throw new IOException("Error encoding CHOICE: No element of CHOICE was selected.");
		}

		public int decode(InputStream is) throws IOException {
			return decode(is, null);
		}

		public int decode(InputStream is, BerTag berTag) throws IOException {

			int codeLength = 0;
			BerTag passedTag = berTag;

			if (berTag == null) {
				berTag = new BerTag();
				codeLength += berTag.decode(is);
			}

			if (berTag.equals(BerTag.CONTEXT_CLASS, BerTag.CONSTRUCTED, 0)) {
				codeLength += BerLength.skip(is);
				positiveResult = new FspGetParameter();
				codeLength += positiveResult.decode(is, null);
				return codeLength;
			}

			if (berTag.equals(BerTag.CONTEXT_CLASS, BerTag.CONSTRUCTED, 1)) {
				codeLength += BerLength.skip(is);
				negativeResult = new DiagnosticFspGet();
				codeLength += negativeResult.decode(is, null);
				return codeLength;
			}

			if (passedTag != null) {
				return 0;
			}

			throw new IOException("Error decoding CHOICE: Tag " + berTag + " matched to no item.");
		}

		public void encodeAndSave(int encodingSizeGuess) throws IOException {
			ReverseByteArrayOutputStream reverseOS = new ReverseByteArrayOutputStream(encodingSizeGuess);
			encode(reverseOS);
			code = reverseOS.getArray();
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			appendAsString(sb, 0);
			return sb.toString();
		}

		public void appendAsString(StringBuilder sb, int indentLevel) {

			if (positiveResult != null) {
				sb.append("positiveResult: ");
				positiveResult.appendAsString(sb, indentLevel + 1);
				return;
			}

			if (negativeResult != null) {
				sb.append("negativeResult: ");
				negativeResult.appendAsString(sb, indentLevel + 1);
				return;
			}

			sb.append("<none>");
		}

	}

	public static final BerTag tag = new BerTag(BerTag.UNIVERSAL_CLASS, BerTag.CONSTRUCTED, 16);

	public byte[] code = null;
	private Credentials performerCredentials = null;
	private InvokeId invokeId = null;
	private Result result = null;
	
	public FspGetParameterReturn() {
	}

	public FspGetParameterReturn(byte[] code) {
		this.code = code;
	}

	public void setPerformerCredentials(Credentials performerCredentials) {
		this.performerCredentials = performerCredentials;
	}

	public Credentials getPerformerCredentials() {
		return performerCredentials;
	}

	public void setInvokeId(InvokeId invokeId) {
		this.invokeId = invokeId;
	}

	public InvokeId getInvokeId() {
		return invokeId;
	}

	public void setResult(Result result) {
		this.result = result;
	}

	public Result getResult() {
		return result;
	}

	public int encode(OutputStream reverseOS) throws IOException {
		return encode(reverseOS, true);
	}

	public int encode(OutputStream reverseOS, boolean withTag) throws IOException {

		if (code != null) {
			for (int i = code.length - 1; i >= 0; i--) {
				reverseOS.write(code[i]);
			}
			if (withTag) {
				return tag.encode(reverseOS) + code.length;
			}
			return code.length;
		}

		int codeLength = 0;
		codeLength += result.encode(reverseOS);
		
		codeLength += invokeId.encode(reverseOS, true);
		
		codeLength += performerCredentials.encode(reverseOS);
		
		codeLength += BerLength.encodeLength(reverseOS, codeLength);

		if (withTag) {
			codeLength += tag.encode(reverseOS);
		}

		return codeLength;

	}

	public int decode(InputStream is) throws IOException {
		return decode(is, true);
	}

	public int decode(InputStream is, boolean withTag) throws IOException {
		int codeLength = 0;
		int subCodeLength = 0;
		BerTag berTag = new BerTag();

		if (withTag) {
			codeLength += tag.decodeAndCheck(is);
		}

		BerLength length = new BerLength();
		codeLength += length.decode(is);

		int totalLength = length.val;
		codeLength += totalLength;

		subCodeLength += berTag.decode(is);
		performerCredentials = new Credentials();
		subCodeLength += performerCredentials.decode(is, berTag);
		subCodeLength += berTag.decode(is);
		
		if (berTag.equals(InvokeId.tag)) {
			invokeId = new InvokeId();
			subCodeLength += invokeId.decode(is, false);
			subCodeLength += berTag.decode(is);
		}
		else {
			throw new IOException("Tag does not match the mandatory sequence element tag.");
		}
		
		result = new Result();
		subCodeLength += result.decode(is, berTag);
		if (subCodeLength == totalLength) {
			return codeLength;
		}
		throw new IOException("Unexpected end of sequence, length tag: " + totalLength + ", actual sequence length: " + subCodeLength);

		
	}

	public void encodeAndSave(int encodingSizeGuess) throws IOException {
		ReverseByteArrayOutputStream reverseOS = new ReverseByteArrayOutputStream(encodingSizeGuess);
		encode(reverseOS, false);
		code = reverseOS.getArray();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		appendAsString(sb, 0);
		return sb.toString();
	}

	public void appendAsString(StringBuilder sb, int indentLevel) {

		sb.append("{");
		sb.append("\n");
		for (int i = 0; i < indentLevel + 1; i++) {
			sb.append("\t");
		}
		if (performerCredentials != null) {
			sb.append("performerCredentials: ");
			performerCredentials.appendAsString(sb, indentLevel + 1);
		}
		else {
			sb.append("performerCredentials: <empty-required-field>");
		}
		
		sb.append(",\n");
		for (int i = 0; i < indentLevel + 1; i++) {
			sb.append("\t");
		}
		if (invokeId != null) {
			sb.append("invokeId: ").append(invokeId);
		}
		else {
			sb.append("invokeId: <empty-required-field>");
		}
		
		sb.append(",\n");
		for (int i = 0; i < indentLevel + 1; i++) {
			sb.append("\t");
		}
		if (result != null) {
			sb.append("result: ");
			result.appendAsString(sb, indentLevel + 1);
		}
		else {
			sb.append("result: <empty-required-field>");
		}
		
		sb.append("\n");
		for (int i = 0; i < indentLevel; i++) {
			sb.append("\t");
		}
		sb.append("}");
	}

}

