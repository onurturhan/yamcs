import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ContextSwitchPage } from './appbase/pages/ContextSwitchPage';
import { CreateInstancePage1 } from './appbase/pages/CreateInstancePage1';
import { CreateInstancePage2 } from './appbase/pages/CreateInstancePage2';
import { ForbiddenPage } from './appbase/pages/ForbiddenPage';
import { HomePage } from './appbase/pages/HomePage';
import { NotFoundPage } from './appbase/pages/NotFoundPage';
import { ProfilePage } from './appbase/pages/ProfilePage';
import { ServerUnavailablePage } from './appbase/pages/ServerUnavailablePage';
import { AuthGuard } from './core/guards/AuthGuard';
import { ClearContextGuard } from './core/guards/ClearContextGuard';
import { OpenIDCallbackGuard } from './core/guards/OpenIDCallbackGuard';
import { ServerSideOpenIDCallbackGuard } from './core/guards/ServerSideOpenIDCallbackGuard';
import { CustomPreloadingStrategy } from './CustomPreloadingStrategy';

/*
 * Notice that nested modules also have AuthGuards.
 * These will fully execute before other guards in those modules.
 */

const routes: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: HomePage,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: 'create-instance',
        pathMatch: 'full',
        component: CreateInstancePage1,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: 'create-instance/:template',
        component: CreateInstancePage2,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: 'context-switch/:context/:current',
        component: ContextSwitchPage,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: 'profile',
        component: ProfilePage,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: 'storage',
        loadChildren: () => import('src/app/storage/StorageModule').then(m => m.StorageModule),
        canActivate: [AuthGuard],
        data: { 'hasSidebar': false }
      }, {
        path: 'alarms',
        loadChildren: () => import('src/app/alarms/AlarmsModule').then(m => m.AlarmsModule),
        canActivate: [AuthGuard],
      }, {
        path: 'algorithms',
        loadChildren: () => import('src/app/algorithms/AlgorithmsModule').then(m => m.AlgorithmsModule),
        canActivate: [AuthGuard],
      }, {
        path: 'archive',
        loadChildren: () => import('src/app/archive/ArchiveModule').then(m => m.ArchiveModule),
        canActivate: [AuthGuard],
      }, {
        path: 'admin',
        loadChildren: () => import('src/app/admin/AdminModule').then(m => m.AdminModule),
        canActivate: [AuthGuard],
      }, {
        path: 'filetransfer',
        loadChildren: () => import('src/app/filetransfer/FileTransferModule').then(m => m.FileTransferModule),
        canActivate: [AuthGuard],
      }, {
        path: 'commanding',
        loadChildren: () => import('src/app/commanding/CommandingModule').then(m => m.CommandingModule),
        canActivate: [AuthGuard],
      }, {
        path: 'events',
        loadChildren: () => import('src/app/events/EventsModule').then(m => m.EventsModule),
        canActivate: [AuthGuard],
      }, {
        path: 'gaps',
        loadChildren: () => import('src/app/gaps/GapsModule').then(m => m.GapsModule),
        canActivate: [AuthGuard],
      }, {
        path: 'instance',
        loadChildren: () => import('src/app/instancehome/InstanceHomeModule').then(m => m.InstanceHomeModule),
        canActivate: [AuthGuard],
        data: { preload: true },
      }, {
        path: 'links',
        loadChildren: () => import('src/app/links/LinksModule').then(m => m.LinksModule),
        canActivate: [AuthGuard],
      }, {
        path: 'timeline',
        loadChildren: () => import('src/app/timeline/TimelineModule').then(m => m.TimelineModule),
        canActivate: [AuthGuard],
      }, {
        path: 'telemetry',
        loadChildren: () => import('src/app/telemetry/TelemetryModule').then(m => m.TelemetryModule),
        canActivate: [AuthGuard],
        data: { preload: true },
      }, {
        path: 'mdb',
        loadChildren: () => import('src/app/mdb/MdbModule').then(m => m.MdbModule),
        canActivate: [AuthGuard],
      }, {
        path: 'cb',
        canActivate: [ClearContextGuard, OpenIDCallbackGuard],
        children: [],
        data: { 'hasSidebar': false }
      }, {
        path: 'oidc-browser-callback',
        canActivate: [ClearContextGuard, ServerSideOpenIDCallbackGuard],
        children: [],
        data: { 'hasSidebar': false }
      }, {
        path: 'down',
        component: ServerUnavailablePage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: '403',
        component: ForbiddenPage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      }, {
        path: '**',
        component: NotFoundPage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      },
    ]
  },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      onSameUrlNavigation: 'reload',
      preloadingStrategy: CustomPreloadingStrategy,
      relativeLinkResolution: 'legacy'
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule { }
