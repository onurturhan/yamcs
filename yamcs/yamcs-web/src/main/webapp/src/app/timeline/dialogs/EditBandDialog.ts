import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject, Subscription } from 'rxjs';
import { UpdateTimelineBandRequest } from '../../client/types/timeline';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './EditBandDialog.html',
  styleUrls: ['./EditBandDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditBandDialog implements OnDestroy {

  form: FormGroup;
  dirty$ = new BehaviorSubject<boolean>(false);

  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<EditBandDialog>,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    formBuilder: FormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const band = data.band;
    this.form = formBuilder.group({
      name: [band.name, [Validators.required]],
      description: [band.description || ''],
      tags: [band.tags || []],
      properties: formBuilder.group({}), // Properties are added in sub-components
    });
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  doOnConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      shared: this.data.band.shared,
      tags: formValue.tags,
      properties: formValue.properties,
    };

    this.yamcs.yamcsClient.updateTimelineBand(this.yamcs.instance!, this.data.band.id, options)
      .then(band => this.dialogRef.close(band))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
