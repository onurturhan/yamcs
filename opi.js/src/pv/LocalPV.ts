import { PV } from './PV';
import { PVEngine } from './PVEngine';

export class LocalPV extends PV {

    constructor(name: string, pvEngine: PVEngine, readonly initializer?: any) {
        super(name, pvEngine);
        this.writable = true;
    }
}
