import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { StorageClient, Transfer, TransferSubscription } from '../client';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

@Component({
  templateUrl: './OngoingTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OngoingTransfersTab implements OnDestroy {

  serviceName$ = new BehaviorSubject<string | null>(null);
  dataSource = new MatTableDataSource<TransferItem>();

  private storageClient: StorageClient;
  private transfersById = new Map<number, TransferItem>();
  private transferSubscription: TransferSubscription;

  private dirty = false;
  private syncSubscription: Subscription;

  private queryParamSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    route: ActivatedRoute,
    synchronizer: Synchronizer,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.queryParamSubscription = route.queryParamMap.subscribe(params => {
      const service = params.get('service');
      this.serviceName$.next(service);
      this.switchService(service);
    });
    this.syncSubscription = synchronizer.sync(() => {
      if (this.dirty) {
        const values = [...this.transfersById.values()];
        values.sort((a, b) => this.compareTransfers(a.transfer, b.transfer));
        this.dataSource.data = values;
        this.dirty = false;
      }
    });
  }

  private compareTransfers(a: Transfer, b: Transfer) {
    // Transfer.state is the primary sort key (RUNNING state on top).
    // Creation time or start time is secondary sort key.
    if (a.state != b.state) {
      if (a.state == 'RUNNING') {
        return -1;
      }
      if (b.state == 'RUNNING') {
        return 1;
      }
    }
    const time1 = a.creationTime || a.startTime || "";
    const time2 = b.creationTime || b.startTime || "";
    // most recent transfers on top
    return time2.localeCompare(time1)
  }

  private switchService(service: string | null) {
    // Clear state
    this.dirty = false;
    this.transfersById.clear();
    this.dataSource.data = [];
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }

    if (service) {
      this.transferSubscription = this.yamcs.yamcsClient.createTransferSubscription({
        instance: this.yamcs.instance!,
        serviceName: service,
      }, transfer => {
        switch (transfer.state) {
          case 'RUNNING':
          case 'PAUSED':
          case 'CANCELLING':
          case 'QUEUED':
            this.setOrUpdate(transfer);
            break;
          case 'FAILED':
          case 'COMPLETED':
            this.transfersById.delete(transfer.id);
            break;
        }

        // throttle updates, it can get spammy
        this.dirty = true;
      });
    }
  }

  // Do our best to preserve top-level object identity
  // It improves change detection behaviour
  private setOrUpdate(transfer: Transfer) {
    let item = this.transfersById.get(transfer.id);
    if (item) {
      item.updateTransfer(transfer);
    } else {
      const objectUrl = this.storageClient.getObjectURL(
        '_global', transfer.bucket, transfer.objectName);
      item = new TransferItem(transfer, objectUrl);
      this.transfersById.set(transfer.id, item);
    }
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    if (this.queryParamSubscription) {
      this.queryParamSubscription.unsubscribe();
    }
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }
  }
}
