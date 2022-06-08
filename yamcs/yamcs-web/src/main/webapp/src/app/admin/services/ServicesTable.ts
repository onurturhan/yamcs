import { AfterViewInit, ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Service } from '../../client';

@Component({
  selector: 'app-services-table',
  templateUrl: './ServicesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesTable implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['state', 'name', 'className', 'actions'];

  @Input()
  dataSource = new MatTableDataSource<Service>();

  @Input()
  readonly = false;

  @Output()
  startService = new EventEmitter<string>();

  @Output()
  stopService = new EventEmitter<string>();

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
