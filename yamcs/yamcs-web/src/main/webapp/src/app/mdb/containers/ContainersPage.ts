import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Container, GetContainersOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { ContainersDataSource } from './ContainersDataSource';

@Component({
  templateUrl: './ContainersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersPage implements AfterViewInit {

  shortName = false;
  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  filterControl = new FormControl();

  dataSource: ContainersDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'maxInterval', label: 'Max Interval', visible: true },
    { id: 'sizeInBits', label: 'Size in bits', visible: true },
    { id: 'baseContainer', label: 'Base Container', visible: true },
    { id: 'restrictionCriteria', label: 'Restriction Criteria', visible: true },
    { id: 'shortDescription', label: 'Description' },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  private selection = new SelectionModel<Container>(false);

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Containers');
    this.dataSource = new ContainersDataSource(yamcs);
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    this.filterControl.setValue(queryParams.get('filter'));

    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });

    if (queryParams.has('page')) {
      this.paginator.pageIndex = Number(queryParams.get('page'));
    }
    this.updateDataSource();
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });
  }

  private updateDataSource() {
    this.updateURL();
    const options: GetContainersOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadContainers(options).then(() => {
      this.selection.clear();
    });
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  selectNext() {
    const items = this.dataSource.containers$.value;
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
    const items = this.dataSource.containers$.value;
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
      const items = this.dataSource.containers$.value;
      if (items.indexOf(item) !== -1) {
        this.router.navigate(['/mdb/containers', item.qualifiedName], {
          queryParams: { c: this.yamcs.context }
        });
      }
    }
  }
}