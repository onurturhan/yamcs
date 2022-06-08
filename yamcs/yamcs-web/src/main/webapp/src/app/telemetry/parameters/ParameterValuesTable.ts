import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParameterDataDataSource } from './ParameterDataDataSource';


@Component({
  selector: 'app-parameter-values-table',
  templateUrl: './ParameterValuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterValuesTable {

  @Input()
  dataSource: ParameterDataDataSource;

  displayedColumns = [
    'severity',
    'generationTime',
    // 'receptionTime', // Only works for pcache, not parchive.
    'rawValue',
    'engValue',
    'rangeCondition',
    'acquisitionStatus',
    'actions',
  ];
}
