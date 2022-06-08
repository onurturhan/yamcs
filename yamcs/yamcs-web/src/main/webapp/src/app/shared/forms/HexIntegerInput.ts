import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, Validator } from '@angular/forms';

@Component({
  selector: 'app-hex-integer-input',
  templateUrl: './HexIntegerInput.html',
  styleUrls: ['./HexIntegerInput.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => HexIntegerInput),
      multi: true,
    }, {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => HexIntegerInput),
      multi: true,
    },
  ]
})
export class HexIntegerInput implements ControlValueAccessor, Validator {

  @ViewChild('input', { static: true })
  private inputComponent: ElementRef;

  private onChange = (_: number | null) => { };

  // Called for initial values, assuming decimal
  writeValue(value: any) {
    if (value) {
      const numberValue = Number(value);
      if (!isNaN(numberValue)) {
        this.inputComponent.nativeElement.value = numberValue.toString(16);
        this.fireChange();
      }
    }
  }

  fireChange() {
    try {
      const numberValue = this.createNumberOrThrow();
      this.onChange(numberValue);
    } catch {
      this.onChange(NaN);
    }
  }

  private createNumberOrThrow() {
    const hexValue = this.inputComponent.nativeElement.value;
    if (!hexValue) {
      return null;
    }

    if (/^[a-fA-F0-9]+$/.test(hexValue)) {
      return parseInt(hexValue, 16);
    } else {
      throw new Error('Invalid hex pattern');
    }
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  validate(control: FormControl) {
    if (!control.value) {
      return null;
    }
    return isNaN(control.value) ? { 'notHex': true } : null;
  }
}
