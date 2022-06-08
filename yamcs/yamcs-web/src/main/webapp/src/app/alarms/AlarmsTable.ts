import { SelectionModel } from '@angular/cdk/collections';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Alarm } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { AlarmsDataSource } from './AlarmsDataSource';

@Component({
  selector: 'app-alarms-table',
  templateUrl: './AlarmsTable.html',
  styleUrls: ['./AlarmsTable.css'],
})
export class AlarmsTable {

  displayedColumns = [
    'state',
    'severity',
    'time',
    'alarm',
    'type',
    'trip_value',
    'live_value',
    'actions',
  ];

  @Input()
  dataSource: AlarmsDataSource;

  @Input()
  selection: SelectionModel<Alarm>;

  @Input()
  view: 'standard' | 'unacknowledged' | 'acknowledged' | 'shelved' | 'all' = 'standard';

  @Input()
  mayControl = false;

  @Output()
  acknowledgeAlarm = new EventEmitter<Alarm>();

  @Output()
  shelveAlarm = new EventEmitter<Alarm>();

  @Output()
  unshelveAlarm = new EventEmitter<Alarm>();

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => {
    return `${alarm.triggerTime}__${alarm.id.namespace}__${alarm.id.name}__${alarm.seqNum}`;
  };

  constructor(readonly yamcs: YamcsService) {
  }

  toggleOne(row: Alarm) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  hideAlarm(alarm: Alarm) {
    if (this.view === 'standard') {
      return !!alarm.shelveInfo;
    } else if (this.view === 'all') {
      return false;
    } else if (this.view === 'unacknowledged') {
      return !!alarm.shelveInfo || alarm.acknowledged;
    } else if (this.view === 'acknowledged') {
      return !!alarm.shelveInfo || !alarm.acknowledged;
    } else if (this.view === 'shelved') {
      return !alarm.shelveInfo;
    }
  }
}
