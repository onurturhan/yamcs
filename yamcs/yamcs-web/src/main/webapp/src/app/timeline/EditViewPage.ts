import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { TimelineBand, UpdateTimelineViewRequest } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './EditViewPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditViewPage implements OnDestroy {

  form: FormGroup;

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    readonly location: Location,
    formBuilder: FormBuilder,
  ) {
    title.setTitle('Edit View');
    const id = route.snapshot.paramMap.get('view')!;
    this.form = formBuilder.group({
      name: [null, Validators.required],
      bands: [null, []],
    });
    yamcs.yamcsClient.getTimelineView(yamcs.instance!, id)
      .then(view => {
        this.form.setValue({
          name: view.name,
          bands: view.bands || [],
        });
        this.formSubscription = this.form.valueChanges.subscribe(() => {
          this.dirty$.next(true);
        });
      }).catch(err => this.messageService.showError(err));
  }

  onConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineViewRequest = {
      name: formValue.name,
      bands: formValue.bands.map((band: TimelineBand) => band.id),
    };
    const id = this.route.snapshot.paramMap.get('view')!;
    this.yamcs.yamcsClient.updateTimelineView(this.yamcs.instance!, id, options)
      .then(() => this.router.navigateByUrl(`/timeline/views?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
