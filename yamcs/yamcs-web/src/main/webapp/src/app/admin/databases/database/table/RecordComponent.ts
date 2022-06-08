import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Record, Table } from '../../../../client';

@Component({
  selector: 'app-record',
  templateUrl: './RecordComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecordComponent {

  @Input()
  table: Table;

  @Input()
  record: Record;
}
