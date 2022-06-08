import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Parameter, ParameterSubscription, ParameterValue, Value } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { UnitsPipe } from '../../shared/pipes/UnitsPipe';
import { ValuePipe } from '../../shared/pipes/ValuePipe';
import { SetParameterDialog } from './SetParameterDialog';


@Component({
  templateUrl: './ParameterPage.html',
  styleUrls: ['./ParameterPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterPage implements OnDestroy {

  config: WebsiteConfig;
  parameter$ = new BehaviorSubject<Parameter | null>(null);
  offset$ = new BehaviorSubject<string | null>(null);

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: ParameterSubscription;

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private messageService: MessageService,
    private dialog: MatDialog,
    private title: Title,
    private valuePipe: ValuePipe,
    private unitsPipe: UnitsPipe,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeParameter(qualifiedName);
    });
  }

  changeParameter(qualifiedName: string) {
    this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, qualifiedName).then(parameter => {
      this.parameter$.next(parameter);

      if (qualifiedName !== parameter.qualifiedName) {
        this.offset$.next(qualifiedName.substring(parameter.qualifiedName.length));
      } else {
        this.offset$.next(null);
      }

      this.updateTitle();
    }).catch(err => {
      this.messageService.showError(err);
    });

    if (this.parameterValueSubscription) {
      this.parameterValueSubscription.cancel();
    }

    this.parameterValueSubscription = this.yamcs.yamcsClient.createParameterSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      id: [{ name: qualifiedName }],
      abortOnInvalid: false,
      sendFromCache: true,
      updateOnExpiration: true,
      action: 'REPLACE',
    }, data => {
      this.parameterValue$.next(data.values ? data.values[0] : null);
      this.updateTitle();
    });
  }

  updateTitle() {
    const parameter = this.parameter$.getValue();
    const offset = this.offset$.getValue();
    if (parameter) {
      let title = parameter.name;
      if (offset) {
        title += offset;
      }
      const pval = this.parameterValue$.getValue();
      if (pval) {
        title += ': ' + this.valuePipe.transform(pval.engValue);
        if (parameter.type && parameter.type.unitSet) {
          title += ' ' + this.unitsPipe.transform(parameter.type.unitSet);
        }
        if (pval.rangeCondition && pval.rangeCondition === 'LOW') {
          title += ' ↓';
        } else if (pval.rangeCondition && pval.rangeCondition === 'HIGH') {
          title += ' ↑';
        }
      }
      this.title.setTitle(title);
    }
  }

  isWritable() {
    const parameter = this.parameter$.value;
    if (parameter) {
      return parameter.dataSource === 'LOCAL'
        || parameter.dataSource === 'EXTERNAL1'
        || parameter.dataSource === 'EXTERNAL2'
        || parameter.dataSource === 'EXTERNAL3';
    }
    return false;
  }

  maySetParameter() {
    const parameter = this.parameter$.value;
    if (parameter) {
      return this.authService.getUser()!.hasObjectPrivilege('WriteParameter', parameter.qualifiedName);
    }
    return false;
  }

  mayReadAlarms() {
    return this.authService.getUser()!.hasSystemPrivilege('ReadAlarms');
  }

  mayReadMissionDatabase() {
    return this.authService.getUser()!.hasSystemPrivilege('GetMissionDatabase');
  }

  setParameter() {
    const parameter = this.parameter$.value!;
    const dialogRef = this.dialog.open(SetParameterDialog, {
      width: '400px',
      data: {
        parameter: this.parameter$.value
      }
    });
    dialogRef.afterClosed().subscribe((value: Value) => {
      if (value) {
        this.yamcs.yamcsClient
          .setParameterValue(this.yamcs.instance!, this.yamcs.processor!, parameter.qualifiedName, value)
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  ngOnDestroy() {
    if (this.parameterValueSubscription) {
      this.parameterValueSubscription.cancel();
    }
  }
}
