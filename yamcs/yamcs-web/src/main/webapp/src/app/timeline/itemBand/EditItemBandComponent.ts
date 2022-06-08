import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { TimelineBand } from '../../client/types/timeline';
import { YamcsService } from '../../core/services/YamcsService';
import { addDefaultItemBandProperties } from './ItemBandStyles';

@Component({
  selector: 'app-edit-item-band',
  templateUrl: './EditItemBandComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditItemBandComponent implements AfterViewInit {

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
    const props = addDefaultItemBandProperties(this.band.properties || {});

    // Angular does not seem to have form.addGroup. So we get creative.
    // The properties sub-group is set in the parent component, and here
    // we append to it in a roundabout way.

    const propConfig: any = {
      frozen: [props.frozen, [Validators.required]],
      itemBackgroundColor: [props.itemBackgroundColor, [Validators.required]],
      itemBorderColor: [props.itemBorderColor, [Validators.required]],
      itemBorderWidth: [props.itemBorderWidth, [Validators.required]],
      itemCornerRadius: [props.itemCornerRadius, [Validators.required]],
      itemHeight: [props.itemHeight, [Validators.required]],
      itemMarginLeft: [props.itemMarginLeft, [Validators.required]],
      itemTextColor: [props.itemTextColor, [Validators.required]],
      itemTextOverflow: [props.itemTextOverflow, [Validators.required]],
      itemTextSize: [props.itemTextSize, [Validators.required]],
      marginBottom: [props.marginBottom, [Validators.required]],
      marginTop: [props.marginTop, [Validators.required]],
      multiline: [props.multiline, [Validators.required]],
      spaceBetweenItems: [props.spaceBetweenItems, [Validators.required]],
      spaceBetweenLines: [props.spaceBetweenLines, [Validators.required]],
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
