import { ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { FormulaCompiler } from '@yamcs/opi';
import { BehaviorSubject, Subscription } from 'rxjs';
import { NamedObjectId, ParameterSubscription } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';

@Component({
  selector: 'app-live-expression',
  template: '{{ result$ | async }}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LiveExpression implements OnInit, OnDestroy {

  @Input()
  expression: string;

  result$ = new BehaviorSubject<any>(null);

  private subscription: ParameterSubscription;
  private dirty = false;
  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, private synchronizer: Synchronizer) {
  }

  ngOnInit() {
    const compiler = new FormulaCompiler();
    const script = compiler.compile('=' + this.expression);

    const parameters = script.getPVNames();
    if (parameters.length) {
      const ids = parameters.map(parameter => ({ name: parameter }));
      let idMapping: { [key: number]: NamedObjectId; };
      this.subscription = this.yamcs.yamcsClient.createParameterSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        id: ids,
        abortOnInvalid: true,
        sendFromCache: true,
        updateOnExpiration: true,
        action: 'REPLACE',
      }, data => {
        if (data.mapping) {
          idMapping = {
            ...idMapping,
            ...data.mapping,
          };
        }
        for (const pval of (data.values || [])) {
          if (pval.engValue) {
            const id = idMapping[pval.numericId];
            script.updateDataSource(id.name, {
              value: utils.convertValue(pval.engValue),
              acquisitionStatus: pval.acquisitionStatus,
            });
          }
        }

        if (this.result$.value === null) { // First value: fast page update
          const output = script.execute();
          this.result$.next(output);
        } else { // Throttle follow-on updates
          this.dirty = true;
        }
      });
    } else {
      const output = script.execute();
      this.result$.next(output);
    }

    this.syncSubscription = this.synchronizer.sync(() => {
      if (this.dirty) {
        const output = script.execute();
        this.result$.next(output);
        this.dirty = false;
      }
    });
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.cancel();
    }
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
