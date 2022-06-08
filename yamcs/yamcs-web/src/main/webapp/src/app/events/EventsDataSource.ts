import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Event, EventSubscription, GetEventsOptions } from '../client';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';
import { EventBuffer } from './EventBuffer';

export interface AnimatableEvent extends Event {
  animate?: boolean;
}

export class EventsDataSource extends DataSource<AnimatableEvent> {

  pageSize = 100;
  offscreenRecord: Event | null;
  options: GetEventsOptions;
  blockHasMore = false;

  events$ = new BehaviorSubject<Event[]>([]);
  private eventBuffer: EventBuffer;

  public loading$ = new BehaviorSubject<boolean>(false);
  public streaming$ = new BehaviorSubject<boolean>(false);

  private realtimeSubscription: EventSubscription;
  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.eventBuffer.dirty && !this.loading$.getValue()) {
        this.events$.next(this.eventBuffer.snapshot());
        this.eventBuffer.dirty = false;
      }
    });

    this.eventBuffer = new EventBuffer(() => {

      // Best solution for now, alternative is to re-establish
      // the offscreenRecord after compacting.
      this.blockHasMore = true;

      this.eventBuffer.compact(500);
    });
  }

  connect() {
    return this.events$;
  }

  loadEvents(options: GetEventsOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(events => {
      this.loading$.next(false);
      this.eventBuffer.reset();
      this.blockHasMore = false;
      this.eventBuffer.addArchiveData(events);
    });
  }

  hasMore() {
    return this.offscreenRecord != null && !this.blockHasMore;
  }

  /**
   * Fetches a page of data and keeps track of one invisible record that
   * allows to deterimine if there are further page(s). The next to last
   * record is used to determine the stop date of the next query because
   * the server uses the interval bounds: [start,stop)
   */
  private loadPage(options: GetEventsOptions) {
    this.options = options;
    return this.yamcs.yamcsClient.getEvents(this.yamcs.instance!, options).then(events => {
      if (events.length > this.pageSize) {
        events.splice(events.length - 1, 1);
        this.offscreenRecord = events[events.length - 1];
      } else {
        this.offscreenRecord = null;
      }
      return events;
    });
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData(options: GetEventsOptions) {
    if (!this.offscreenRecord) {
      return;
    }
    this.loadPage({
      ...options,
      stop: this.offscreenRecord.generationTime,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(events => {
      this.eventBuffer.addArchiveData(events);
    });
  }

  startStreaming() {
    this.streaming$.next(true);
    this.realtimeSubscription = this.yamcs.yamcsClient.createEventSubscription({
      instance: this.yamcs.instance!,
    }, event => {
      if (!this.loading$.getValue() && this.matchesFilter(event)) {
        (event as AnimatableEvent).animate = true;
        this.eventBuffer.addRealtimeEvent(event);
      }
    });
  }

  private matchesFilter(event: Event) {
    if (this.options) {
      if (this.options.source) {
        if (event.source !== this.options.source) {
          return false;
        }
      }
      if (this.options.q) {
        if (event.message.indexOf(this.options.q) === -1) {
          return false;
        }
      }
      if (this.options.severity) {
        switch (this.options.severity) {
          case 'SEVERE':
          case 'ERROR':
            if (event.severity === 'CRITICAL') {
              return false;
            }
          // fall
          case 'CRITICAL':
            if (event.severity === 'DISTRESS') {
              return false;
            }
          // fall
          case 'DISTRESS':
            if (event.severity === 'WARNING') {
              return false;
            }
          // fall
          case 'WARNING':
            if (event.severity === 'WATCH') {
              return false;
            }
          // fall
          case 'WATCH':
            if (event.severity === 'INFO') {
              return false;
            }
        }
      }
    }

    return true;
  }

  stopStreaming() {
    if (this.realtimeSubscription) {
      this.realtimeSubscription.cancel();
    }
    this.streaming$.next(false);
  }

  disconnect() {
    this.stopStreaming();
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    this.events$.complete();
    this.loading$.complete();
    this.streaming$.complete();
  }
}
