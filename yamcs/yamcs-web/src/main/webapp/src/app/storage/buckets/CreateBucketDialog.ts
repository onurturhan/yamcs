import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-create-bucket-dialog',
  templateUrl: './CreateBucketDialog.html',
})
export class CreateBucketDialog {

  form: FormGroup;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateBucketDialog>,
    formBuilder: FormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private messageService: MessageService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.form = formBuilder.group({
      name: ['', Validators.required],
    });
  }

  save() {
    this.storageClient.createBucket(this.data.bucketInstance, {
      name: this.form.value['name'],
    }).then(() => this.dialogRef.close(true))
      .catch(err => {
        this.dialogRef.close(false);
        this.messageService.showError(err);
      });
  }
}
