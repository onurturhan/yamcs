
export interface CreateTimelineItemRequest {
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
  tags?: string[];
}

export interface UpdateTimelineItemRequest {
  name: string;
  start: string;
  duration: string;
  tags?: string[];
}

export type TimelineItemType = 'EVENT' | 'MANUAL_ACTIVITY' | 'AUTO_ACTIVITY';

export interface TimelineItem {
  id: string;
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
}

export interface TimelineViewsPage {
  views: TimelineView[];
}

export interface TimelineBandsPage {
  bands: TimelineBand[];
}

export interface TimelineTagsPage {
  tags: string[];
}

export interface TimelineItemsPage {
  items: TimelineItem[];
}

export interface GetTimelineItemsOptions {
  source: string;
  start?: string;
  stop?: string;
  band?: string;
}

export type TimelineBandType = 'TIME_RULER' | 'ITEM_BAND' | 'SPACER' | 'COMMAND_BAND';

export interface CreateTimelineBandRequest {
  name: string;
  description: string;
  type: TimelineBandType;
  shared: boolean;
  tags?: string[];
  properties?: { [key: string]: string; };
}

export interface TimelineBand {
  id: string;
  type: TimelineBandType,
  shared: boolean;
  name: string;
  description: string;
  tags?: string[];
  properties?: { [key: string]: string; };
  username: string;
}

export interface UpdateTimelineBandRequest {
  name: string;
  description: string;
  shared: boolean;
  tags?: string[];
  properties?: { [key: string]: string; };
}

export interface TimelineView {
  id: string;
  name: string;
  bands?: TimelineBand[];
}

export interface CreateTimelineViewRequest {
  name: string;
  bands?: string[];
}

export interface UpdateTimelineViewRequest {
  name: string;
  bands?: string[];
}
