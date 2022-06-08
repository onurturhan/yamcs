import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ThreadInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ThreadPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ThreadPage {

  thread$ = new BehaviorSubject<ThreadInfo | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
  ) {

    route.paramMap.subscribe(params => {
      const id = params.get('id')!;
      this.changeThread(Number(id));
    });
  }

  private changeThread(id: number) {
    this.yamcs.yamcsClient.getThread(id).then(thread => {
      this.thread$.next(thread);
      this.title.setTitle(thread.name);
    });
  }
}
