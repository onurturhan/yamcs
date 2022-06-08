import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { ClearContextGuard } from '../core/guards/ClearContextGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { AdminActivityPage } from './activity/AdminActivityPage';
import { DatabasePage } from './databases/database/DatabasePage';
import { DatabaseShellTab } from './databases/database/shell/DatabaseShellTab';
import { StreamColumnsTab } from './databases/database/stream/StreamColumnsTab';
import { StreamDataTab } from './databases/database/stream/StreamDataTab';
import { StreamPage } from './databases/database/stream/StreamPage';
import { StreamScriptTab } from './databases/database/stream/StreamScriptTab';
import { DatabaseStreamsTab } from './databases/database/streams/DatabaseStreamsTab';
import { TableDataTab } from './databases/database/table/TableDataTab';
import { TableInfoTab } from './databases/database/table/TableInfoTab';
import { TablePage } from './databases/database/table/TablePage';
import { TableScriptTab } from './databases/database/table/TableScriptTab';
import { DatabaseTablesTab } from './databases/database/tables/DatabaseTablesTab';
import { DatabasesPage } from './databases/databases/DatabasesPage';
import { HttpTrafficPage } from './http-traffic/HttpTrafficPage';
import { CreateGroupPage } from './iam/CreateGroupPage';
import { CreateServiceAccountPage } from './iam/CreateServiceAccountPage';
import { CreateUserPage } from './iam/CreateUserPage';
import { EditGroupPage } from './iam/EditGroupPage';
import { EditUserPage } from './iam/EditUserPage';
import { GroupPage } from './iam/GroupPage';
import { GroupsPage } from './iam/GroupsPage';
import { RolePage } from './iam/RolePage';
import { RolesPage } from './iam/RolesPage';
import { ServiceAccountsPage } from './iam/ServiceAccountsPage';
import { UserPage } from './iam/UserPage';
import { UsersPage } from './iam/UsersPage';
import { LeapSecondsPage } from './leap-seconds/LeapSecondsPage';
import { ProcessorTypesPage } from './processor-types/ProcessorTypesPage';
import { ReplicationPage } from './replication/ReplicationPage';
import { RocksDbDatabasePage } from './rocksdb/RocksDbDatabasePage';
import { RocksDbDatabasesPage } from './rocksdb/RocksDbDatabasesPage';
import { RoutesPage } from './routes/RoutesPage';
import { ServicesPage } from './services/ServicesPage';
import { SessionsPage } from './sessions/SessionsPage';
import { AdminPage } from './shared/AdminPage';
import { SystemPage } from './system/SystemPage';
import { ThreadPage } from './threads/ThreadPage';
import { ThreadsPage } from './threads/ThreadsPage';

const routes: Routes = [{
  path: '',
  canActivate: [AuthGuard, ClearContextGuard, SuperuserGuard],
  canActivateChild: [AuthGuard],
  runGuardsAndResolvers: 'always',
  component: AdminPage,
  children: [{
    path: '',
    pathMatch: 'full',
    component: AdminActivityPage,
  }, {
    path: 'http-traffic',
    component: HttpTrafficPage,
  }, {
    path: 'sessions',
    component: SessionsPage,
  }, {
    path: 'routes',
    component: RoutesPage,
  }, {
    path: 'leap-seconds',
    component: LeapSecondsPage,
  }, {
    path: 'processor-types',
    component: ProcessorTypesPage,
  }, {
    path: 'replication',
    component: ReplicationPage,
  }, {
    path: 'services',
    component: ServicesPage,
  }, {
    path: 'databases',
    children: [{
      path: '',
      pathMatch: 'full',
      component: DatabasesPage,
    }, {
      path: ':database',
      component: DatabasePage,
      children: [{
        path: '',
        pathMatch: 'full',
        redirectTo: 'tables',
      }, {
        path: 'tables',
        pathMatch: 'full',
        component: DatabaseTablesTab,
      }, {
        path: 'tables/:table',
        component: TablePage,
        children: [{
          path: '',
          pathMatch: 'full',
          redirectTo: 'info',
        }, {
          path: 'info',
          component: TableInfoTab,
        }, {
          path: 'data',
          component: TableDataTab,
        }, {
          path: 'script',
          component: TableScriptTab,
        }],
      }, {
        path: 'shell',
        pathMatch: 'full',
        component: DatabaseShellTab,
      }, {
        path: 'streams',
        pathMatch: 'full',
        component: DatabaseStreamsTab,
      }, {
        path: 'streams/:stream',
        component: StreamPage,
        children: [{
          path: '',
          pathMatch: 'full',
          redirectTo: 'columns',
        }, {
          path: 'columns',
          component: StreamColumnsTab,
        }, {
          path: 'data',
          component: StreamDataTab,
        }, {
          path: 'script',
          component: StreamScriptTab,
        }],
      }]
    }]
  }, {
    path: 'rocksdb',
    runGuardsAndResolvers: 'always',
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'databases'
    }, {
      path: 'databases',
      pathMatch: 'full',
      component: RocksDbDatabasesPage,
    }, {
      path: 'databases/:tablespace',
      children: [{
        path: '**',
        component: RocksDbDatabasePage,
      }]
    }],
  }, {
    path: 'iam/service-accounts',
    pathMatch: 'full',
    component: ServiceAccountsPage,
  }, {
    path: 'iam/service-accounts/create',
    pathMatch: 'full',
    component: CreateServiceAccountPage,
  }, {
    path: 'iam/users',
    pathMatch: 'full',
    component: UsersPage,
  }, {
    path: 'iam/users/create',
    pathMatch: 'full',
    component: CreateUserPage,
  }, {
    path: 'iam/users/:username',
    pathMatch: 'full',
    component: UserPage,
  }, {
    path: 'iam/users/:username/edit',
    pathMatch: 'full',
    component: EditUserPage,
  }, {
    path: 'iam/groups',
    pathMatch: 'full',
    component: GroupsPage,
  }, {
    path: 'iam/groups/create',
    pathMatch: 'full',
    component: CreateGroupPage,
  }, {
    path: 'iam/groups/:name',
    pathMatch: 'full',
    component: GroupPage,
  }, {
    path: 'iam/groups/:name/edit',
    pathMatch: 'full',
    component: EditGroupPage,
  }, {
    path: 'iam/roles',
    pathMatch: 'full',
    component: RolesPage,
  }, {
    path: 'iam/roles/:name',
    pathMatch: 'full',
    component: RolePage,
  }, {
    path: 'threads',
    pathMatch: 'full',
    component: ThreadsPage,
  }, {
    path: 'threads/:id',
    component: ThreadPage,
  }, {
    path: 'system',
    pathMatch: 'full',
    component: SystemPage,
  }]
}];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule { }

export const routingComponents = [
  AdminActivityPage,
  CreateGroupPage,
  CreateServiceAccountPage,
  CreateUserPage,
  DatabasesPage,
  DatabasePage,
  DatabaseShellTab,
  DatabaseStreamsTab,
  DatabaseTablesTab,
  EditGroupPage,
  EditUserPage,
  GroupsPage,
  GroupPage,
  HttpTrafficPage,
  LeapSecondsPage,
  ProcessorTypesPage,
  ReplicationPage,
  RocksDbDatabasesPage,
  RocksDbDatabasePage,
  RolesPage,
  RolePage,
  RoutesPage,
  ServiceAccountsPage,
  ServicesPage,
  SessionsPage,
  StreamPage,
  StreamColumnsTab,
  StreamDataTab,
  StreamScriptTab,
  SystemPage,
  TablePage,
  TableInfoTab,
  TableDataTab,
  TableScriptTab,
  ThreadsPage,
  ThreadPage,
  UsersPage,
  UserPage,
];
