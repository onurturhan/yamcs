import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { Alarm } from '../client';
import { AuthService } from '../core/services/AuthService';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';
import { TrackBySelectionModel } from '../shared/table/TrackBySelectionModel';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmsDataSource } from './AlarmsDataSource';
import { ShelveAlarmDialog } from './ShelveAlarmDialog';

@Component({
  templateUrl: './AlarmsPage.html',
  styleUrls: ['./AlarmsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPage implements OnDestroy {

  filterForm = new FormGroup({
    filter: new FormControl(),
    view: new FormControl('standard'),
  });

  // Alarm to show in detail pane (only on single selection)
  detailAlarm$ = new BehaviorSubject<Alarm | null>(null);

  dataSource: AlarmsDataSource;
  selection = new TrackBySelectionModel<Alarm>((index: number, alarm: Alarm) => {
    return `${alarm.triggerTime}__${alarm.id.namespace}__${alarm.id.name}__${alarm.seqNum}`;
  }, false, []);

  viewOptions: Option[] = [
    { id: 'standard', label: 'Standard view (ack & unack)' },
    { id: 'unacknowledged', label: 'Unacknowledged alarms' },
    { id: 'acknowledged', label: 'Acknowledged alarms' },
    { id: 'shelved', label: 'Shelved alarms' },
    { id: 'all', label: 'All alarms' },
  ];

  private selectionSubscription: Subscription;
  private alarmsSubscription: Subscription;

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  view$ = new BehaviorSubject('standard');
  private filter: string;

  constructor(
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    title: Title,
    private dialog: MatDialog,
    private authService: AuthService,
    private messageService: MessageService,
  ) {
    title.setTitle('Alarms');
    this.selectionSubscription = this.selection.changed.subscribe(() => {
      const selected = this.selection.selected;
      if (selected.length === 1) {
        this.detailAlarm$.next(selected[0]);
      } else {
        this.detailAlarm$.next(null);
      }
    });

    this.dataSource = new AlarmsDataSource(this.yamcs);
    this.dataSource.loadAlarms();

    this.alarmsSubscription = this.dataSource.alarms$.subscribe(alarms => {
      this.selection.matchNewValues(alarms);

      // Update detail pane
      const detailAlarm = this.detailAlarm$.value;
      if (detailAlarm) {
        for (const alarm of alarms) {
          if (this.isSameAlarm(alarm, detailAlarm)) {
            this.detailAlarm$.next(alarm);
            break;
          }
        }
      }
    });

    this.initializeOptions();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.filter = filter;
      this.updateURL();
      this.dataSource.setFilter(filter);
    });

    this.filterForm.get('view')!.valueChanges.forEach(view => {
      this.view$.next(view);
      this.updateURL();
      this.selection.clear();
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('view')) {
      const view = queryParams.get('view')!;
      this.view$.next(view);
      this.filterForm.get('view')!.setValue(view);
    }
  }

  private isSameAlarm(alarm1: Alarm, alarm2: Alarm) {
    return alarm1.seqNum === alarm2.seqNum
      && alarm1.id.namespace === alarm2.id.namespace
      && alarm1.id.name === alarm2.id.name
      && alarm1.triggerTime === alarm2.triggerTime;
  }

  acknowledgeAlarms(alarms: Alarm[]) {
    this.dialog.open(AcknowledgeAlarmDialog, {
      width: '400px',
      data: { alarms },
    }).afterClosed().subscribe(() => this.selection.clear());
  }

  shelveAlarms(alarms: Alarm[]) {
    this.dialog.open(ShelveAlarmDialog, {
      width: '400px',
      data: { alarms },
    }).afterClosed().subscribe(() => this.selection.clear());
  }

  unshelveAlarms(alarms: Alarm[]) {
    for (const alarm of alarms) {
      const alarmName = alarm.id.namespace + (alarm.id.name ? '/' + alarm.id.name : '');
      this.yamcs.yamcsClient.unshelveAlarm(this.yamcs.instance!, this.yamcs.processor!, alarmName, alarm.seqNum)
        .then(() => this.selection.clear())
        .catch(err => this.messageService.showError(err));
    }
  }

  clearAlarms(alarms: Alarm[]) {
    for (const alarm of alarms) {
      const alarmName = alarm.id.namespace + (alarm.id.name ? '/' + alarm.id.name : '');
      this.yamcs.yamcsClient.clearAlarm(this.yamcs.instance!, this.yamcs.processor!, alarmName, alarm.seqNum, {});
    }
  }

  mayControlAlarms() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlAlarms');
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: this.filter || null,
        view: this.view$.value || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  ngOnDestroy() {
    if (this.selectionSubscription) {
      this.selectionSubscription.unsubscribe();
    }
    if (this.alarmsSubscription) {
      this.alarmsSubscription.unsubscribe();
    }
  }
}
