import { ChangeDetectionStrategy, ChangeDetectorRef, Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import tznames from '../tznames';
import { Option } from './Select';

@Component({
  selector: 'app-timezone-select',
  templateUrl: './TimezoneSelect.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TimezoneSelect),
      multi: true,
    }
  ]
})
export class TimezoneSelect implements ControlValueAccessor {

  areaOptions: Option[] = [];
  areaControl = new FormControl();

  locationControl = new FormControl();
  locationOptionsByArea: { [key: string]: Array<Option>; } = {};
  locationOptions$ = new BehaviorSubject<Option[]>([]);

  sublocationControl = new FormControl();
  sublocationOptionsByLocation: { [key: string]: Array<Option>; } = {};
  sublocationOptions$ = new BehaviorSubject<Option[]>([]);

  private onChange = (_: string | null) => { };

  constructor(private changeDetection: ChangeDetectorRef) {
    this.processTznames();
    this.areaControl.valueChanges.subscribe(() => {
      const area = this.areaControl.value || null;
      if (this.hasLocations(area)) {
        const locationOptions = this.locationOptionsByArea[area];
        this.locationOptions$.next(locationOptions);
        this.locationControl.setValue(locationOptions[0].id);
      } else {
        this.locationOptions$.next([]);
        this.sublocationOptions$.next([]);
        this.onChange(area);
      }
    });
    this.locationControl.valueChanges.subscribe(() => {
      const area = this.areaControl.value || null;
      if (area) {
        const location = this.locationControl.value;
        if (location) {
          if (this.hasSublocations(location)) {
            const sublocationOptions = this.sublocationOptionsByLocation[location];
            this.sublocationOptions$.next(sublocationOptions);
            this.sublocationControl.setValue(sublocationOptions[0].id);
          } else {
            this.sublocationOptions$.next([]);
            this.onChange(`${area}/${location}`);
          }
        } else {
          this.onChange(null);
        }
      } else {
        this.onChange(null);
      }
    });
    this.sublocationControl.valueChanges.subscribe(() => {
      const area = this.areaControl.value || null;
      const location = this.locationControl.value || null;
      const sublocation = this.sublocationControl.value || null;
      this.onChange(sublocation ? `${area}/${location}/${sublocation}` : null);
    });
  }

  private processTznames() {
    const areas: string[] = [];
    const locations: string[] = [];
    for (const tz of tznames) {
      let idx = tz.indexOf('/');
      if (idx === -1) {
        areas.push(tz);
        this.areaOptions.push({ id: tz, label: tz });
      } else {
        const area = tz.substring(0, idx);
        if (areas.indexOf(area) === -1) {
          areas.push(area);
          this.areaOptions.push({ id: area, label: area });
          this.locationOptionsByArea[area] = [];
        }
        let location = tz.substring(idx + 1);
        idx = location.indexOf('/');
        if (idx === -1) {
          this.locationOptionsByArea[area].push({ id: location, label: location });
        } else {
          const sublocation = location.substring(idx + 1);
          location = location.substring(0, idx);
          if (locations.indexOf(location) === -1) {
            locations.push(location);
            this.locationOptionsByArea[area].push({ id: location, label: location });
            this.sublocationOptionsByLocation[location] = [];
          }
          this.sublocationOptionsByLocation[location].push({ id: sublocation, label: sublocation });
        }
      }
    }
  }

  private hasLocations(area: string | null) {
    return area && (area in this.locationOptionsByArea);
  }

  private hasSublocations(location: string | null) {
    return location && (location in this.sublocationOptionsByLocation);
  }

  writeValue(value: any) {
    if (value) {
      const parts = value.split('/', 3);
      this.areaControl.setValue(parts[0]);
      if (parts.length > 1) {
        this.locationControl.setValue(parts[1]);
      }
      if (parts.length > 2) {
        this.sublocationControl.setValue(parts[2]);
      }
      this.onChange(value);
    } else {
      this.areaControl.setValue(null);
      this.locationControl.setValue(null);
      this.sublocationControl.setValue(null);
      this.onChange(null);
    }
    this.changeDetection.detectChanges();
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }
}
