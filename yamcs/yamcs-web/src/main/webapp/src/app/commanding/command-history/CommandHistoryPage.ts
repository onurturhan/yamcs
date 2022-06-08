import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { rowAnimation } from '../../animations';
import { GetCommandHistoryOptions } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { MessageService } from '../../core/services/MessageService';
import { PrintService } from '../../core/services/PrintService';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { Option, Select } from '../../shared/forms/Select';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { User } from '../../shared/User';
import * as utils from '../../shared/utils';
import { subtractDuration } from '../../shared/utils';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';
import { CommandHistoryPrintable } from './CommandHistoryPrintable';
import { CommandHistoryRecord } from './CommandHistoryRecord';


const defaultInterval = 'PT1H';

@Component({
  templateUrl: './CommandHistoryPage.html',
  styleUrls: ['./CommandHistoryPage.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandHistoryPage {

  selectedRecord$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  @ViewChild('intervalSelect')
  intervalSelect: Select;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new FormGroup({
    filter: new FormControl(),
    queue: new FormControl('ANY'),
    interval: new FormControl(defaultInterval),
    customStart: new FormControl(null),
    customStop: new FormControl(null),
  });

  dataSource: CommandHistoryDataSource;

  columns: ColumnInfo[] = [
    { id: 'commandId', label: 'ID' },
    { id: 'generationTime', label: 'Time', alwaysVisible: true },
    { id: 'comment', label: 'Comment', visible: true },
    { id: 'command', label: 'Command', alwaysVisible: true },
    { id: 'issuer', label: 'Issuer' },
    { id: 'queue', label: 'Queue' },
    { id: 'queued', label: 'Queued', visible: true },
    { id: 'released', label: 'Released', visible: true },
    { id: 'sent', label: 'Sent', visible: true },
    { id: 'acknowledgments', label: 'Extra acknowledgments', visible: true },
    { id: 'completion', label: 'Completion', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  queueOptions: Option[];

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private filter: string;
  private queue: string;

  user: User;
  config: WebsiteConfig;

  constructor(
    readonly yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    private messageService: MessageService,
    private router: Router,
    private route: ActivatedRoute,
    private printService: PrintService,
    title: Title,
    synchronizer: Synchronizer,
    private clipboard: Clipboard,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;
    title.setTitle('Command History');

    this.dataSource = new CommandHistoryDataSource(this.yamcs, synchronizer);

    this.queueOptions = [
      { id: 'ANY', label: 'Any queue' },
    ];
    for (const queueName of this.config.queueNames) {
      this.queueOptions.push({ id: queueName, label: queueName });
    }

    this.initializeOptions();
    this.loadData();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.filter = filter;
      this.loadData();
    });

    this.filterForm.get('queue')!.valueChanges.forEach(queue => {
      this.queue = (queue !== 'ANY') ? queue : null;
      this.loadData();
    });

    this.filterForm.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || this.yamcs.getMissionTime();
        const customStop = this.validStop || this.yamcs.getMissionTime();
        this.filterForm.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filterForm.get('customStop')!.setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = yamcs.getMissionTime();
        this.validStart = subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('queue')) {
      this.queue = queryParams.get('queue')!;
      this.filterForm.get('queue')!.setValue(this.queue);
    }
    if (queryParams.has('interval')) {
      this.appliedInterval = queryParams.get('interval')!;
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = queryParams.get('customStart')!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = new Date(customStart);
        const customStop = queryParams.get('customStop')!;
        this.filterForm.get('customStop')!.setValue(customStop);
        this.validStop = new Date(customStop);
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = subtractDuration(this.validStop, this.appliedInterval);
      }
    } else {
      this.appliedInterval = defaultInterval;
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = subtractDuration(this.validStop, defaultInterval);
    }
  }

  jumpToNow() {
    const interval = this.filterForm.value['interval'];
    if (interval === 'NO_LIMIT') {
      // NO_LIMIT may include future data under erratic conditions. Reverting
      // to the default interval is more in line with the wording 'jump to now'.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else if (interval === 'CUSTOM') {
      // For simplicity reasons, just reset to default 1h interval.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else {
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  applyCustomDates() {
    this.validStart = utils.toDate(this.filterForm.value['customStart']);
    this.validStop = utils.toDate(this.filterForm.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  loadData() {
    this.updateURL();
    const options: GetCommandHistoryOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }
    if (this.queue) {
      options.queue = this.queue;
    }
    this.dataSource.loadEntries(options)
      .catch(err => this.messageService.showError(err));
  }

  loadMoreData() {
    const options: GetCommandHistoryOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }
    if (this.queue) {
      options.queue = this.queue;
    }

    this.dataSource.loadMoreData(options)
      .catch(err => this.messageService.showError(err));
  }

  showResend() {
    return this.config.tc && this.user.hasAnyObjectPrivilegeOfType('Command');
  }

  showCommandExports() {
    return this.config.commandExports;
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: this.filter || null,
        queue: this.queue || null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  copyHex(command: CommandHistoryRecord) {
    const hex = utils.convertBase64ToHex(command.binary);
    this.clipboard.copy(hex);
  }

  copyBinary(command: CommandHistoryRecord) {
    const raw = atob(command.binary);
    this.clipboard.copy(raw);
  }

  selectRecord(rec: CommandHistoryRecord) {
    this.selectedRecord$.next(rec);
  }

  printReport() {
    const data = this.dataSource.records$.value.slice().reverse();
    this.printService.printComponent(CommandHistoryPrintable, 'Command Report', data);
  }
}
