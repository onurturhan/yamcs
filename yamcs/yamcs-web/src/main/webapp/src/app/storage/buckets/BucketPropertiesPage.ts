import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Bucket, StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './BucketPropertiesPage.html',
  styleUrls: ['./BucketPropertiesPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketPropertiesPage {

  bucketInstance: string;
  name: string;

  bucket$ = new BehaviorSubject<Bucket | null>(null);
  private storageClient: StorageClient;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    title: Title,
  ) {
    this.bucketInstance = route.snapshot.parent!.paramMap.get('instance')!;
    this.name = route.snapshot.parent!.paramMap.get('name')!;
    title.setTitle(this.name + ': Properties');
    this.storageClient = yamcs.createStorageClient();
    this.storageClient.getBucket(this.bucketInstance, this.name).then(bucket => {
      this.bucket$.next(bucket);
    });
  }

  bucketSizePercentage(bucket: Bucket, ceil = false) {
    var pct = 100 * bucket.size / bucket.maxSize;
    return ceil ? Math.min(100, pct) : pct;
  }

  objectCountPercentage(bucket: Bucket, ceil = false) {
    var pct = 100 * bucket.numObjects / bucket.maxObjects;
    return ceil ? Math.min(100, pct) : pct;
  }

  zeroOrMore(value: number) {
    return Math.max(0, value);
  }
}
