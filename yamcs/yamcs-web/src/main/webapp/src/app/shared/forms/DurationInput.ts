import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator, Validators } from '@angular/forms';
import { Option } from './Select';

// Used as a signal to show validation results
const INVALID_PROTOSTRING = 'invalid';

@Component({
  selector: 'app-duration-input',
  templateUrl: './DurationInput.html',
  styleUrls: ['./DurationInput.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DurationInput),
      multi: true,
    }, {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => DurationInput),
      multi: true,
    },
  ]
})
export class DurationInput implements ControlValueAccessor, Validator {

  resolutionOptions: Option[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];

  @ViewChild('input', { static: true })
  private inputComponent: ElementRef;

  private onChange = (_: string | null) => { };

  resolutionControl: FormControl;

  constructor(
  ) {
    this.resolutionControl = new FormControl('seconds', Validators.required);
    this.resolutionControl.valueChanges.subscribe(() => this.fireChange());
  }

  writeValue(value: any) {
    if (value) {
      // Don't show trailing 's'
      const seconds = value.substring(0, value.length - 1);
      this.inputComponent.nativeElement.value = seconds;
      this.fireChange();
    }
  }

  fireChange() {
    try {
      const duration = this.createDurationOrThrow();
      this.onChange(duration || null);
    } catch {
      // Trigger a validation error
      this.onChange(INVALID_PROTOSTRING);
    }
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  validate(control: FormControl): ValidationErrors | null {
    if (control.value === INVALID_PROTOSTRING) {
      return { duration: true };
    }
    return null;
  }

  private createDurationOrThrow() {
    const durationInput = this.inputComponent.nativeElement.value;
    const resolutionInput = this.resolutionControl.value;
    if (durationInput === '' || !resolutionInput) {
      return null;
    }

    if (!isFloat(durationInput)) {
      throw new Error('Invalid duration pattern');
    }

    let durationInSeconds;
    switch (resolutionInput) {
      case 'seconds':
        durationInSeconds = durationInput;
        break;
      case 'minutes':
        durationInSeconds = durationInput * 60;
        break;
      case 'hours':
        durationInSeconds = durationInput * 60 * 60;
        break;
      default:
        throw new Error(`Unexpected resolution ${resolutionInput}`);
    }

    return durationInSeconds + 's';
  }
}

function isFloat(value: any) {
  return !isNaN(value) && (Number.isInteger(parseFloat(value)) || value % 1 !== 0);
}
