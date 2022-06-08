import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { SelectParameterDialog } from '../../shared/dialogs/SelectParameterDialog';
import { ExportArchiveDataDialog } from './ExportArchiveDataDialog';
import { ParameterTableViewer } from './ParameterTableViewer';

@Component({
  selector: 'app-parameter-table-viewer-controls',
  templateUrl: './ParameterTableViewerControls.html',
})
export class ParameterTableViewerControls {

  private bucket: string;

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: ParameterTableViewer;

  constructor(
    private dialog: MatDialog,
    private snackbar: MatSnackBar,
    private authService: AuthService,
    configService: ConfigService,
  ) {
    this.bucket = configService.getConfig().displayBucket;
  }

  public init(viewer: ParameterTableViewer) {
    this.viewer = viewer;
    this.initialized$.next(true);
  }

  addParameter() {
    const dialogRef = this.dialog.open(SelectParameterDialog, {
      width: '500px',
      data: {
        okLabel: 'ADD',
        exclude: this.viewer.getModel().parameters,
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.viewer.addParameter(result);
      }
    });
  }

  mayManageDisplays() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', this.bucket)
      || user.hasSystemPrivilege('ManageAnyBucket');
  }

  save() {
    this.viewer.save().then(() => {
      this.snackbar.open('Changes saved', undefined, {
        duration: 1000,
      });
    }).catch(err => {
      this.snackbar.open('Failed to save changes: ' + err);
    });
  }

  exportArchiveData() {
    let parameterIds = this.viewer.selection.selected;
    if (!parameterIds.length) {
      parameterIds = this.viewer.getModel().parameters;
    }
    this.dialog.open(ExportArchiveDataDialog, {
      width: '400px',
      data: {
        parameterIds,
      }
    });
  }
}
