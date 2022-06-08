import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer } from '@angular/platform-browser';
import { HelpDialog } from '../dialogs/HelpDialog';

@Component({
  selector: 'app-help',
  templateUrl: './Help.html',
  styleUrls: ['./Help.css'],
})
export class Help {

  @Input()
  dialogTitle: string;

  @ViewChild('dialogContent', { static: true })
  dialogContent: ElementRef;

  constructor(private dialog: MatDialog, private sanitizer: DomSanitizer) {
  }

  showHelp() {
    const html = this.dialogContent.nativeElement.innerHTML;
    this.dialog.open(HelpDialog, {
      width: '500px',
      data: {
        title: this.dialogTitle,
        content: this.sanitizer.bypassSecurityTrustHtml(html),
      }
    });

    // Prevent further click handling.
    // (for example because this component was used in a <label/>)
    return false;
  }
}
