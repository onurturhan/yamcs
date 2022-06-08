import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';
import { generateRandomName } from '../../shared/utils';

@Component({
  selector: 'app-start-replay-dialog',
  templateUrl: './StartReplayDialog.html',
})
export class StartReplayDialog {

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<StartReplayDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {

    let initialStart = yamcs.getMissionTime();
    let initialStop;

    if (this.data) {
      if (this.data.start) {
        initialStart = this.data.start;
      }
      if (this.data.stop) {
        initialStop = this.data.stop;
      }
    }

    this.form = formBuilder.group({
      name: [generateRandomName(), Validators.required],
      start: [utils.toISOString(initialStart), [
        Validators.required,
      ]],
      stop: [initialStop ? utils.toISOString(initialStop) : ''],
    });
  }

  start() {
    const replayConfig: { [key: string]: any; } = {
      start: utils.toISOString(this.form.value.start),
    };
    if (this.form.value.stop) {
      replayConfig.stop = utils.toISOString(this.form.value.stop);
    }

    this.dialogRef.close({
      instance: this.yamcs.instance!,
      name: this.form.value.name,
      type: 'Archive', // TODO make configurable?
      persistent: true,
      config: JSON.stringify(replayConfig),
    });
  }
}
