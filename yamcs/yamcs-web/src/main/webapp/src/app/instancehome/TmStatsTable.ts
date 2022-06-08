import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { TmStatistics } from '../client';
import { ConfigService, WebsiteConfig } from '../core/services/ConfigService';
import { YamcsService } from '../core/services/YamcsService';

export interface PacketStats {
  packetName: string;
  packetRate: number;
  dataRate: number;
  lastReceived?: string;
  lastPacketTime?: string;
}

@Component({
  selector: 'app-tmstats-table',
  templateUrl: './TmStatsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TmStatsTable implements AfterViewInit, OnDestroy {

  @Input()
  tmstats$: Observable<TmStatistics[]>;
  tmstatsSubscription: Subscription;

  @ViewChild(MatSort)
  sort: MatSort;

  private statsByName: { [key: string]: PacketStats; } = {};
  dataSource = new MatTableDataSource<PacketStats>();
  totalPacketRate$ = new BehaviorSubject<number>(0);
  totalDataRate$ = new BehaviorSubject<number>(0);

  config: WebsiteConfig;

  displayedColumns = [
    'packetName',
    'lastPacketTime',
    'lastReceived',
    'packetRate',
    'dataRate',
    'actions',
  ];

  constructor(readonly yamcs: YamcsService, configService: ConfigService) {
    this.config = configService.getConfig();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    if (this.tmstats$ && !this.tmstatsSubscription) {
      this.yamcs.yamcsClient.getPacketNames(this.yamcs.instance!).then(packetNames => {
        for (const packetName of (packetNames || [])) {
          this.statsByName[packetName] = { packetName, packetRate: 0, dataRate: 0 };
        }
        this.updateData();

        this.tmstatsSubscription = this.tmstats$.subscribe(tmstats => {
          for (const entry of (tmstats || [])) {
            this.statsByName[entry.packetName] = entry;
          }
          this.updateData();
        });
      });
    }
  }

  private updateData() {
    const lines = Object.values(this.statsByName);
    let totalPacketRate = 0;
    let totalDataRate = 0;
    for (const line of lines) {
      totalPacketRate += Number(line.packetRate);
      totalDataRate += Number(line.dataRate);
    }
    this.totalPacketRate$.next(totalPacketRate);
    this.totalDataRate$.next(totalDataRate);
    this.dataSource.data = lines;
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
  }
}
