import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, forwardRef, Input, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { BehaviorSubject } from 'rxjs';
import { Command, GetCommandsOptions } from '../../client';
import { CommandsDataSource } from '../../commanding/command-sender/CommandsDataSource';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnChooser, ColumnInfo } from '../template/ColumnChooser';
import { SearchFilter } from './SearchFilter';

@Component({
  selector: 'app-command-selector',
  templateUrl: './CommandSelector.html',
  styleUrls: ['./CommandSelector.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CommandSelector),
      multi: true
    }
  ]
})
export class CommandSelector implements ControlValueAccessor, AfterViewInit {

  @Input()
  path: string;

  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(ColumnChooser)
  columnChooser: ColumnChooser;

  @ViewChild('searchFilter')
  searchFilter: SearchFilter;

  filterControl = new FormControl();

  dataSource: CommandsDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'significance', label: 'Significance', visible: true },
    { id: 'shortDescription', label: 'Description' },
  ];

  // Added dynamically based on actual commands.
  aliasColumns$ = new BehaviorSubject<ColumnInfo[]>([]);

  private selection = new SelectionModel<ListItem>(false);
  selectedCommand$ = new BehaviorSubject<ListItem | null>(null);

  private onChange = (_: Command | null) => { };
  private onTouched = () => { };

  constructor(readonly yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.dataSource = new CommandsDataSource(yamcs);
    this.selectedCommand$.subscribe(item => {
      if (item && item.command) {
        return this.onChange(item.command);
      } else {
        return this.onChange(null);
      }
    });
  }

  ngAfterViewInit() {
    this.changeSystem('');
    this.searchFilter.filter.nativeElement.focus();
    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });
  }

  changeSystem(system: string, page = 0) {
    this.system = system;
    this.updateBrowsePath();
    this.paginator.pageIndex = page;
    this.updateDataSource();
  }

  private updateDataSource() {
    const options: GetCommandsOptions = {
      system: this.system || '/',
      noAbstract: true,
      details: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadCommands(options).then(() => {
      this.selection.clear();
      this.updateBrowsePath();

      // Reset alias columns
      for (const aliasColumn of this.aliasColumns$.value) {
        const idx = this.columns.indexOf(aliasColumn);
        if (idx !== -1) {
          this.columns.splice(idx, 1);
        }
      }
      const aliasColumns = [];
      for (const namespace of this.dataSource.getAliasNamespaces()) {
        const aliasColumn = { id: namespace, label: namespace, alwaysVisible: true };
        aliasColumns.push(aliasColumn);
      }
      this.columns.splice(1, 0, ...aliasColumns); // Insert after name column
      this.aliasColumns$.next(aliasColumns);
      this.columnChooser.recalculate(this.columns);
    });
  }

  selectRow(row: ListItem) {
    if (row.spaceSystem) {
      this.selectedCommand$.next(null);
      this.changeSystem(row.name);
    } else {
      this.selectedCommand$.next(row);
    }
    return false;
  }

  private updateBrowsePath() {
    const breadcrumb: BreadCrumbItem[] = [];
    let path = '';
    if (this.system) {
      for (const part of this.system.slice(1).split('/')) {
        path += '/' + part;
        breadcrumb.push({
          name: part,
          system: path,
        });
      }
    }
    this.breadcrumb$.next(breadcrumb);
  }

  selectNext() {
    const items = this.dataSource.items$.value;
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
    const items = this.dataSource.items$.value;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.max(items.indexOf(currentItem) - 1, 0);
      }
    }
    this.selection.select(items[idx]);
  }

  applySelection() {
    if (this.selection.hasValue()) {
      const item = this.selection.selected[0];
      const items = this.dataSource.items$.value;
      if (item.command && items.indexOf(item) !== -1) {
        this.selectRow(item);
      }
    }
  }

  writeValue(value: any) {
    this.path = value;
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }
}

export class ListItem {
  spaceSystem: boolean;
  name: string;
  command?: Command;
}

export interface BreadCrumbItem {
  name?: string;
  system: string;
}
