import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Database } from '../../../../client';
import { YamcsService } from '../../../../core/services/YamcsService';
import { Shell } from '../../shell/Shell';
import { ResultSetPrinter } from './ResultSetPrinter';

@Component({
  templateUrl: './DatabaseShellTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabaseShellTab implements AfterViewInit {

  @ViewChild(Shell)
  private shell: Shell;

  private database: Database;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('database')!;
    this.changeDatabase(name);
  }

  ngAfterViewInit() {
    this.shell.printLine('Yamcs DB Shell');
    this.shell.printLine('Type help or ? for help.\n');
  }

  handleCommand(command: string) {
    if (!command) {
      return;
    }
    const statements = command.split(';').map(s => s.trim());
    for (const statement of statements) {
      if (statement) {
        this.handleStatement(statement);
      }
    }
  }

  private changeDatabase(name: string) {
    this.yamcs.yamcsClient.getDatabase(name).then(database => {
      this.database = database;
      this.shell.ready$.next(true);
      this.shell.setPrompt(database.name + '> ');
    });
  }

  private handleStatement(statement: string) {
    if (statement === '?' || statement.startsWith('help')) {
      const parts = statement.split(/\s+/, 2);
      if (parts.length > 1) {
        switch (parts[1]) {
          case 'use':
            this.shell.printLine('Use another database, provided as argument.');
            break;
          default:
            this.shell.printLine('*** No help on ' + parts[1]);
        }
      } else {
        this.shell.printLine();
        this.shell.printLine('List of dbshell commands:');
        this.shell.printLine('?        Show help.');
        this.shell.printLine('help     List available commands with "help" or detailed help with "help cmd".');
        this.shell.printLine('use      Use another database, provided as argument.');
        this.shell.printLine();
      }
    } else if (statement.startsWith('use')) {
      const parts = statement.split(/\s+/, 2);
      if (parts.length === 1) {
        this.shell.printLine('*** Missing argument');
      } else {
        this.changeDatabase(parts[1]);
      }
    } else {
      this.yamcs.yamcsClient.executeSQL(this.database.name, statement).then(resultSet => {
        if (resultSet.rows) {
          const printer = new ResultSetPrinter(resultSet.columns || []);
          for (const row of (resultSet.rows || [])) {
            printer.add(row);
          }
          this.shell.printLines(printer.printPending());
          this.shell.printLines(printer.printSummary());
        } else {
          this.shell.printLine('Empty set\n');
        }
      }).catch(err => {
        this.shell.printLine(`${err}`);
      });
    }
  }
}
