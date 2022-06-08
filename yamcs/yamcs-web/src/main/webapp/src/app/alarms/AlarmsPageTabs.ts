import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  selector: 'app-alarms-page-tabs',
  templateUrl: './AlarmsPageTabs.html',
  styleUrls: ['./AlarmsPageTabs.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPageTabs {
  constructor(readonly yamcs: YamcsService) {
  }
}
