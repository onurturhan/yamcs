import { CommandAssignment, CommandHistoryEntry } from '../../client';
import * as utils from '../../shared/utils';
import { Acknowledgment } from './Acknowledgment';

export class CommandHistoryRecord {

  private entry: CommandHistoryEntry;

  id: string;

  generationTime: string;
  origin: string;
  sequenceNumber: number;

  commandName: string;

  assignments: CommandAssignment[] = [];
  userAssignments: CommandAssignment[] = [];

  username: string;

  unprocessedBinary: string;
  binary: string;

  comment?: string;
  queue?: string;

  transmissionConstraints?: Acknowledgment;

  queued?: Acknowledgment;
  released?: Acknowledgment;
  sent?: Acknowledgment;
  extraAcks: Acknowledgment[] = [];

  completed?: Acknowledgment;

  extra: { [key: string]: string; }[] = [];

  private acksByName: { [key: string]: Acknowledgment; } = {};

  constructor(entry: CommandHistoryEntry) {
    this.entry = entry;
    this.id = entry.id;
    this.generationTime = entry.generationTime;
    this.origin = entry.origin;
    this.sequenceNumber = entry.sequenceNumber;
    this.commandName = entry.commandName;

    for (const assignment of (entry.assignments || [])) {
      this.assignments.push(assignment);
      if (assignment.userInput) {
        this.userAssignments.push(assignment);
      }
    }

    for (const attr of entry.attr) {
      if (attr.name === 'username') {
        this.username = attr.value.stringValue!;
      } else if (attr.name === 'source') {
        // Legacy, ignore.
      } else if (attr.name === 'unprocessedBinary') {
        this.unprocessedBinary = attr.value.binaryValue!;
      } else if (attr.name === 'binary') {
        this.binary = attr.value.binaryValue!;
      } else if (attr.name === 'comment') {
        this.comment = attr.value.stringValue;
      } else if (attr.name === 'queue') {
        this.queue = attr.value.stringValue;
      } else if (attr.name.endsWith('_Message')) {
        const ackName = attr.name.substring(0, attr.name.length - '_Message'.length);
        this.saveAckMessage(ackName, attr.value.stringValue!);
      } else if (attr.name.endsWith('_Time')) {
        const ackName = attr.name.substring(0, attr.name.length - '_Time'.length);
        this.saveAckTime(ackName, attr.value.stringValue!);
      } else if (attr.name.endsWith('_Status')) {
        const ackName = attr.name.substring(0, attr.name.length - '_Status'.length);
        this.saveAckStatus(ackName, attr.value.stringValue!);
      } else {
        this.extra.push({ name: attr.name, value: utils.printValue(attr.value) });
      }
    }

    for (const ack of Object.values(this.acksByName)) {
      if (ack.name === 'CommandComplete') {
        this.completed = ack;
      } else if (ack.name === 'TransmissionConstraints') {
        this.transmissionConstraints = ack;
      } else if (ack.name === 'Acknowledge_Queued') {
        this.queued = ack;
      } else if (ack.name === 'Acknowledge_Released') {
        this.released = ack;
      } else if (ack.name === 'Acknowledge_Sent') {
        this.sent = ack;
      } else {
        this.extraAcks.push(ack);
      }
    }
  }

  mergeEntry(entry: CommandHistoryEntry): CommandHistoryRecord {
    const mergedAttr = [
      ...this.entry.attr,
      ...entry.attr,
    ];
    const mergedEntry = {
      ...entry,
      ...this.entry,
      attr: mergedAttr,
    } as CommandHistoryEntry;
    return new CommandHistoryRecord(mergedEntry);
  }

  private saveAckTime(name: string, time: string) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.time = time;
  }

  private saveAckStatus(name: string, status: string) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.status = status;
  }

  private saveAckMessage(name: string, message: string) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.message = message;
  }
}
