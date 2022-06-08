import { Pipe, PipeTransform } from '@angular/core';
import { Packet } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Pipe({ name: 'packetDownloadLink' })
export class PacketDownloadLinkPipe implements PipeTransform {

  constructor(private yamcs: YamcsService) {
  }

  transform(packet: Packet | null): string | null {
    if (!packet) {
      return null;
    }

    const instance = this.yamcs.instance!;
    return this.yamcs.yamcsClient.getPacketDownloadURL(instance, packet.generationTime, packet.sequenceNumber);
  }
}
