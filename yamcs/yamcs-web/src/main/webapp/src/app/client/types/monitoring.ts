import { AlarmRange, NamedObjectId } from './mdb';

export interface Value {
  type: 'AGGREGATE'
  | 'ARRAY'
  | 'BINARY'
  | 'BOOLEAN'
  | 'DOUBLE'
  | 'ENUMERATED'
  | 'FLOAT'
  | 'NONE'
  | 'SINT32'
  | 'SINT64'
  | 'STRING'
  | 'TIMESTAMP'
  | 'UINT32'
  | 'UINT64';
  aggregateValue?: AggregateValue;
  arrayValue?: Value[];
  binaryValue?: string;
  booleanValue?: boolean;
  doubleValue?: number;
  floatValue?: number;
  sint32Value?: number;
  sint64Value?: number;
  stringValue?: string;
  timestampValue?: number;
  uint32Value?: number;
  uint64Value?: number;
}

export interface AggregateValue {
  name: string[];
  value: Value[];
}

export type MonitoringResult = 'DISABLED'
  | 'IN_LIMITS'
  | 'WATCH'
  | 'WARNING'
  | 'DISTRESS'
  | 'CRITICAL'
  | 'SEVERE';

export interface ParameterData {
  parameter: ParameterValue[];
  subscriptionId: number;
}

export interface ParameterValue {
  numericId: number;
  id: NamedObjectId;
  rawValue: Value;
  engValue: Value;
  acquisitionTime: string;
  generationTime: string;

  acquisitionStatus: 'ACQUIRED' | 'NOT_RECEIVED' | 'INVALID' | 'EXPIRED';
  monitoringResult: MonitoringResult;
  alarmRange: AlarmRange[];
  rangeCondition?: 'LOW' | 'HIGH';
  expireMillis: number;
}

export interface Sample {
  time: string;
  avg: number;
  min: number;
  max: number;
  n: number;
}

export interface Range {
  start: string;
  stop: string;
  engValues: Value[];
  counts: number[];
  count: number;
}

export interface GetGapsOptions {
  start?: string;
  stop?: string;
  apids?: number[];
  limit?: number;
}

export interface IssueCommandOptions {
  args?: { [key: string]: any; };
  origin?: string;
  sequenceNumber?: number;
  dryRun?: boolean;
  comment?: string;
  extra?: { [key: string]: Value; };
}

export interface IssueCommandResponse {
  id: string;
  generationTime: string;
  origin: string;
  sequenceNumber: number;
  commandName: string;
  binary: string;
  username: string;
  queue?: string;
}

export interface CommandHistoryAttribute {
  name: string;
  value: Value;
}

export interface CommandAssignment {
  name: string;
  value: Value;
  userInput: boolean;
}

export interface CommandHistoryEntry {
  id: string;
  commandName: string;
  origin: string;
  sequenceNumber: number;
  generationTime: string;
  attr: CommandHistoryAttribute[];
  assignments: CommandAssignment[];
}

export interface CommandHistoryPage {
  entry?: CommandHistoryEntry[];
  continuationToken?: string;
}

export interface GetCommandHistoryOptions {
  start?: string;
  stop?: string;
  limit?: number;
  next?: string;
  q?: string;
  queue?: string;
  order?: 'asc' | 'desc';
}

export interface CreateProcessorRequest {
  instance: string;
  name: string;
  type: string;
  persistent?: boolean;
  config?: string;
}

export interface EditReplayProcessorRequest {
  state?: 'running' | 'paused';
  seek?: string;
  speed?: string;
}

export interface GetPacketsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  name?: string | string[];
  next?: string;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface ListPacketsResponse {
  packet?: Packet[];
  continuationToken?: string;
}

export interface Packet {
  id: NamedObjectId;
  receptionTime: string;
  generationTime: string;
  sequenceNumber: number;
  packet: string;
}

export interface GetParameterValuesOptions {
  start?: string;
  stop?: string;
  pos?: number;
  limit?: number;
  norepeat?: boolean;
  format?: 'csv';
  order?: 'asc' | 'desc';
}

export interface DownloadParameterValuesOptions {
  parameters?: string | string[];
  start?: string;
  stop?: string;
  norepeat?: boolean;
  delimiter?: 'TAB' | 'COMMA' | 'SEMICOLON';
}

export interface GetParameterSamplesOptions {
  start?: string;
  stop?: string;
  count?: number;
  order?: 'asc' | 'desc';
}

export interface GetParameterRangesOptions {
  start?: string;
  stop?: string;
  minGap?: number;
  maxGap?: number;
}

export interface GetPacketIndexOptions {
  start?: string;
  stop?: string;
  mergeTime?: number;
  limit?: number;
}

export interface GetParameterIndexOptions {
  start?: string;
  stop?: string;
  mergeTime?: number;
  limit?: number;
}

export interface GetCommandIndexOptions {
  start?: string;
  stop?: string;
  mergeTime?: number;
  limit?: number;
}

export interface GetEventIndexOptions {
  start?: string;
  stop?: string;
  mergeTime?: number;
  limit?: number;
}

export interface GetCompletenessIndexOptions {
  start?: string;
  stop?: string;
  limit?: number;
}

export interface DownloadPacketsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  name?: string | string[];
  format?: 'raw';
  order?: 'asc' | 'desc';
}

export interface IndexGroup {
  id: NamedObjectId;
  entry: IndexEntry[];
}

export interface IndexEntry {
  start: string;
  stop: string;
  count: number;
}

export interface Gap {
  apid: number;
  start: string;
  stop: string;
  startSequenceCount: number;
  stopSequenceCount: number;
  missingPacketCount: number;
}

export interface ListApidsResponse {
  apids: number[];
}

export interface ListGapsResponse {
  gaps: Gap[];
  continuationToken?: string;
}

export interface PlaybackRange {
  apid: number;
  start: string;
  stop: string;
}

export interface RequestPlaybackRequest {
  ranges: PlaybackRange[];
}

export interface PlaybackInfo {
  interval: string;
  period: string;
  requests: PlaybackRequest[];
}

export interface PlaybackRequest {
  requestTime: string;
  apid: number;
  packetType: number;
  privateHeaderSource: number;
  start: string;
  stop: string;
  status: string;
  tmCount: number;
}
