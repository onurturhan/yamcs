import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Member, Parameter, ParameterType, Value } from '../../client';
import { requireFloat, requireInteger } from '../../shared/forms/validators';
import * as utils from '../../shared/utils';

@Component({
  selector: 'app-set-parameter-dialog',
  templateUrl: './SetParameterDialog.html',
})
export class SetParameterDialog {

  form = new FormGroup({});

  parameter: Parameter;

  constructor(
    private dialogRef: MatDialogRef<SetParameterDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.parameter = data.parameter;
    const validators = this.getValidatorsForType(this.parameter.type!);
    if (this.parameter.type!.engType === 'aggregate') {
      this.addMemberControls(this.parameter.name + '.', this.parameter.type!.member || []);
    } else {
      this.form.addControl(
        this.parameter.name, new FormControl(null, validators));
    }
  }

  private addMemberControls(prefix: string, members: Member[]) {
    for (const member of members) {
      if (member.type.engType === 'aggregate') {
        this.addMemberControls(prefix + member.name + '.', member.type.member || []);
      } else {
        const controlName = prefix + member.name;
        const validators = this.getValidatorsForType(member.type as ParameterType);
        this.form.addControl(
          controlName, new FormControl('', validators));
      }
    }
  }

  private getValidatorsForType(type: ParameterType) {
    const validators = [Validators.required];
    if (type.engType === 'integer') {
      validators.push(requireInteger);
    } else if (type.engType === 'float') {
      validators.push(requireFloat);
    }
    return validators;
  }

  save() {
    const value = this.toValue('', this.parameter);
    this.dialogRef.close(value);
  }

  private toValue(prefix: string, parameter: Parameter | Member): Value {
    const userValue = this.form.value[prefix + parameter.name];
    switch (parameter.type!.engType) {
      case 'boolean':
        return { type: 'BOOLEAN', booleanValue: (userValue === 'true') };
      case 'float':
        return { type: 'FLOAT', floatValue: userValue };
      case 'double':
        return { type: 'DOUBLE', doubleValue: userValue };
      case 'enumeration':
        return { type: 'STRING', stringValue: userValue };
      case 'integer':
        return { type: 'SINT32', sint32Value: userValue };
      case 'string':
        return { type: 'STRING', stringValue: userValue };
      case 'time':
        return { type: 'TIMESTAMP', stringValue: utils.toISOString(userValue) };
      case 'binary':
        return { type: 'BINARY', binaryValue: utils.convertHexToBase64(userValue) };
      case 'aggregate':
        const names: string[] = [];
        const values: Value[] = [];
        for (const member of (parameter.type!.member || [])) {
          names.push(member.name);
          values.push(this.toValue(prefix + parameter.name + '.', member));
        }
        return { type: 'AGGREGATE', aggregateValue: { name: names, value: values } };
      default:
        throw new Error(`Unexpected type: ${parameter.type!.engType}`);
    }
  }
}
