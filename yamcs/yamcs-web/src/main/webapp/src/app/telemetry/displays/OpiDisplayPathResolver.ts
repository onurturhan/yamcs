import { DefaultPathResolver, Display } from '@yamcs/opi';
import { Widget } from '@yamcs/opi/dist/types/Widget';
import { StorageClient } from '../../client';

export class OpiDisplayPathResolver extends DefaultPathResolver {

  constructor(private storageClient: StorageClient, display: Display) {
    super(display);
  }

  resolve(path: string, widget?: Widget) {
    if (path.startsWith('ys://')) {
      const matchResult = path.match(/ys:\/\/([^\\\/]+)\/(.+)/);
      if (matchResult) {
        return this.storageClient.getObjectURL('_global', matchResult[1], matchResult[2]);
      }
    }
    return super.resolve(path, widget);
  }
}
