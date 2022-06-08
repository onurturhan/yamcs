import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { Argument, EnumValue } from '../../client';

@Component({
  selector: 'app-select-enumeration-dialog',
  templateUrl: './SelectEnumerationDialog.html',
  styleUrls: ['./SelectEnumerationDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectEnumerationDialog implements AfterViewInit {

  filterControl = new FormControl();

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<EnumValue>([]);
  selection = new SelectionModel<EnumValue>();

  displayedColumns = [
    'name',
    'value',
  ];

  constructor(
    private dialogRef: MatDialogRef<SelectEnumerationDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const argument = data.argument as Argument;
    const isHex = argument.type.dataEncoding?.encoding === 'UNSIGNED';
    this.dataSource.filterPredicate = (enumValue, filter) => {
      const { label, value } = enumValue;
      return label.toLowerCase().indexOf(filter) >= 0
        || String(value).indexOf(filter) >= 0
        || (isHex && Number(value).toString(16).indexOf(filter) >= 0);
    };

    this.dataSource.data = argument.type.enumValue || [];
    if (isHex) {
      this.displayedColumns.push('hex');
    }
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();
    });

    this.dataSource.paginator = this.paginator;
  }

  selectNext() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.min(items.indexOf(currentItem) + 1, items.length - 1);
      }
    }
    this.selection.select(items[idx]);
  }

  selectPrevious() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.max(items.indexOf(currentItem) - 1, 0);
      }
    }
    this.selection.select(items[idx]);
  }

  toHex(value: string) {
    return Number(value).toString(16);
  }

  applySelection() {
    const selected = this.selection.selected;
    if (selected.length) {
      this.dialogRef.close(selected[0]);
    }
  }
}
