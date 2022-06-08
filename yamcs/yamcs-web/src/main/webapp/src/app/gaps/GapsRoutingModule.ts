import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { GapRequestsPage } from './GapRequestsPage';
import { GapsPage } from './GapsPage';

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
        component: GapsPage,
      }, {
        path: 'requests',
        component: GapRequestsPage,
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class GapsRoutingModule { }

export const routingComponents = [
  GapRequestsPage,
  GapsPage,
];
