import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { DisplayFilePage } from './displays/DisplayFilePage';
import { DisplayFilePageDirtyGuard } from './displays/DisplayFilePageDirtyGuard';
import { DisplayFolderPage } from './displays/DisplayFolderPage';
import { DisplayPage } from './displays/DisplayPage';
import { DisplaysPage } from './displays/DisplaysPage';
import { PacketsPage } from './packets/PacketsPage';
import { ParameterAlarmsTab } from './parameters/ParameterAlarmsTab';
import { ParameterChartTab } from './parameters/ParameterChartTab';
import { ParameterDataTab } from './parameters/ParameterDataTab';
import { ParameterPage } from './parameters/ParameterPage';
import { ParametersPage } from './parameters/ParametersPage';
import { ParameterSummaryTab } from './parameters/ParameterSummaryTab';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, AttachContextGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'home',
      }, {
        path: 'displays',
        pathMatch: 'full',
        redirectTo: 'displays/browse'
      }, {
        path: 'displays/browse',
        component: DisplaysPage,
        children: [
          {
            path: '**',
            component: DisplayFolderPage,
          }
        ]
      }, {
        path: 'displays/files',
        component: DisplayPage,
        children: [
          {
            path: '**',
            component: DisplayFilePage,
            canDeactivate: [DisplayFilePageDirtyGuard],
          }
        ]
      }, {
        path: 'packets',
        component: PacketsPage,
      }, {
        path: 'parameters',
        pathMatch: 'full',
        component: ParametersPage,
      }, {
        path: 'parameters/:qualifiedName',
        component: ParameterPage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'summary'
          }, {
            path: 'summary',
            component: ParameterSummaryTab,
          }, {
            path: 'chart',
            component: ParameterChartTab,
          }, {
            path: 'data',
            component: ParameterDataTab,
          }, {
            path: 'alarms',
            component: ParameterAlarmsTab,
          }
        ]
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    DisplayFilePageDirtyGuard,
  ]
})
export class TelemetryRoutingModule { }

export const routingComponents = [
  DisplaysPage,
  DisplayFilePage,
  DisplayFolderPage,
  DisplayPage,
  PacketsPage,
  ParametersPage,
  ParameterPage,
  ParameterAlarmsTab,
  ParameterDataTab,
  ParameterChartTab,
  ParameterSummaryTab,
];
