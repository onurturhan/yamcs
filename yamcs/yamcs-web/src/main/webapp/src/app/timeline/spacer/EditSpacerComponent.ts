import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { TimelineBand } from '../../client/types/timeline';
import { YamcsService } from '../../core/services/YamcsService';
import { addDefaultSpacerProperties } from './SpacerStyles';

@Component({
  selector: 'app-edit-spacer',
  templateUrl: './EditSpacerComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditSpacerComponent implements AfterViewInit {

  @Input()
  form: FormGroup;

  @Input()
  band: TimelineBand;

  formConfigured$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private changeDetection: ChangeDetectorRef,
  ) { }

  ngAfterViewInit() {
    const props = addDefaultSpacerProperties(this.band.properties || {});

    // Angular does not seem to have form.addGroup. So we get creative.
    // The properties sub-group is set in the parent component, and here
    // we append to it in a roundabout way.

    const propConfig: any = {
      height: [props.height, [Validators.required]],
    };

    const propertiesGroup = this.form.get('properties') as FormGroup;
    for (const controlName in propConfig) {
      const config = propConfig[controlName];
      propertiesGroup.addControl(controlName, new FormControl(config[0], config[1]));
    }

    this.formConfigured$.next(true);
    this.changeDetection.detectChanges();
  }
}
