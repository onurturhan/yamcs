import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { TimelineBand, UpdateTimelineBandRequest } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './EditBandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditBandPage implements OnDestroy {

  form: FormGroup;
  dirty$ = new BehaviorSubject<boolean>(false);

  private formSubscription: Subscription;

  band$: Promise<TimelineBand>;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    formBuilder: FormBuilder,
    readonly location: Location,
  ) {
    title.setTitle('Edit Band');
    const id = route.snapshot.paramMap.get('band')!;
    this.band$ = yamcs.yamcsClient.getTimelineBand(yamcs.instance!, id);
    this.band$.then(band => {
      this.form = formBuilder.group({
        name: [band.name, [Validators.required]],
        description: [band.description || ''],
        tags: [band.tags || []],
        properties: formBuilder.group({}), // Properties are added in sub-components
      });
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.dirty$.next(true);
      });
    }).catch(err => this.messageService.showError(err));
  }

  doOnConfirm() {
    const id = this.route.snapshot.paramMap.get('band')!;
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      shared: true,
      tags: formValue.tags,
      properties: formValue.properties,
    };

    this.yamcs.yamcsClient.updateTimelineBand(this.yamcs.instance!, id, options)
      .then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
