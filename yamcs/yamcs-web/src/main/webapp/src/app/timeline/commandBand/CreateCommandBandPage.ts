import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CreateCommandBandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateCommandBandPage {

  form: FormGroup;

  constructor(
    title: Title,
    formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Command Band');
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({})
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      description: formValue.description,
      type: 'COMMAND_BAND',
      shared: true,
      properties: formValue.properties,
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
