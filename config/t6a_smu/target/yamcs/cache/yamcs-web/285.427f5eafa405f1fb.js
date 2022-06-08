"use strict";(self.webpackChunkapp=self.webpackChunkapp||[]).push([[285],{285:(Wt,Z,a)=>{a.r(Z),a.d(Z,{EventsModule:()=>Lt});var b=a(7573),_=a(1083),A=a(7309),x=a(4375),U=a(1005),N=a(9326),o=a(3075),m=a(1135),w=a(8372),O=a(4e3),I=a(1731),P=a(480),T=a(381),c=a(2134),u=a(8966),t=a(5e3),y=a(141),S=a(4071);let M=(()=>{class i{constructor(e,n,r,d){this.dialogRef=e,this.yamcs=r,this.data=d,this.severityOptions=[{id:"INFO",label:"INFO"},{id:"WATCH",label:"WATCH"},{id:"WARNING",label:"WARNING"},{id:"DISTRESS",label:"DISTRESS"},{id:"CRITICAL",label:"CRITICAL"},{id:"SEVERE",label:"SEVERE"}],this.form=n.group({message:["",o.kI.required],severity:"INFO",time:[c.Y6(r.getMissionTime()),o.kI.required]})}save(){this.yamcs.yamcsClient.createEvent(this.yamcs.instance,{message:this.form.value.message,severity:this.form.value.severity,time:c.Y6(this.form.value.time)}).then(e=>this.dialogRef.close(e))}}return i.\u0275fac=function(e){return new(e||i)(t.Y36(u.so),t.Y36(o.qu),t.Y36(T.C),t.Y36(u.WI))},i.\u0275cmp=t.Xpm({type:i,selectors:[["ng-component"]],decls:21,vars:3,consts:[["mat-dialog-title",""],[1,"mat-typography"],[1,"ya-form",3,"formGroup"],["formControlName","message"],["formControlName","severity",3,"options"],["formControlName","time","step","1"],["align","end"],["mat-dialog-close","",1,"ya-button"],[1,"ya-button","primary",3,"disabled","click"]],template:function(e,n){1&e&&(t.TgZ(0,"h2",0),t._uU(1,"Create event"),t.qZA(),t.TgZ(2,"mat-dialog-content",1),t.TgZ(3,"form",2),t.TgZ(4,"label"),t._uU(5," Message "),t._UZ(6,"textarea",3),t.qZA(),t._UZ(7,"br"),t.TgZ(8,"label"),t._uU(9," Severity"),t._UZ(10,"br"),t._UZ(11,"app-select",4),t.qZA(),t._UZ(12,"br"),t.TgZ(13,"label"),t._uU(14," Event time "),t._UZ(15,"app-date-time-input",5),t.qZA(),t.qZA(),t.qZA(),t.TgZ(16,"mat-dialog-actions",6),t.TgZ(17,"button",7),t._uU(18,"CANCEL"),t.qZA(),t.TgZ(19,"button",8),t.NdJ("click",function(){return n.save()}),t._uU(20,"SAVE"),t.qZA(),t.qZA()),2&e&&(t.xp6(3),t.Q6J("formGroup",n.form),t.xp6(8),t.Q6J("options",n.severityOptions),t.xp6(8),t.Q6J("disabled",!n.form.valid))},directives:[u.uh,u.xY,o._Y,o.JL,o.sg,o.Fj,o.JJ,o.u,y.P,S.Y,u.H8,u.ZT],encapsulation:2,changeDetection:0}),i})();var Y=a(449);class D{constructor(s){this.watermarkObserver=s,this.dirty=!1,this.archiveEvents=[],this.bufferSize=500,this.bufferWatermark=400,this.pointer=0,this.alreadyWarned=!1,this.realtimeBuffer=Array(this.bufferSize).fill(void 0)}addArchiveData(s){this.archiveEvents=this.archiveEvents.concat(s),this.dirty=!0}addRealtimeEvent(s){this.pointer<this.bufferSize&&(this.realtimeBuffer[this.pointer]=s,this.pointer>=this.bufferWatermark&&this.watermarkObserver&&!this.alreadyWarned&&(this.alreadyWarned=!0,this.watermarkObserver()),this.pointer=this.pointer+1),this.dirty=!0}reset(){this.archiveEvents=[],this.realtimeBuffer.fill(void 0),this.pointer=0,this.alreadyWarned=!1,this.dirty=!0}snapshot(){const s=this.realtimeBuffer.filter(n=>void 0!==n);return this.archiveEvents.concat(s).sort((n,r)=>{const d=-n.generationTime.localeCompare(r.generationTime);return 0!==d?d:n.seqNumber-r.seqNumber})}compact(s){const e=this.snapshot();e.length=Math.min(s,e.length),this.reset(),this.archiveEvents=e,this.dirty=!0}}class q extends Y.o2{constructor(s,e){super(),this.yamcs=s,this.pageSize=100,this.blockHasMore=!1,this.events$=new m.X([]),this.loading$=new m.X(!1),this.streaming$=new m.X(!1),this.syncSubscription=e.sync(()=>{this.eventBuffer.dirty&&!this.loading$.getValue()&&(this.events$.next(this.eventBuffer.snapshot()),this.eventBuffer.dirty=!1)}),this.eventBuffer=new D(()=>{this.blockHasMore=!0,this.eventBuffer.compact(500)})}connect(){return this.events$}loadEvents(s){return this.loading$.next(!0),this.loadPage(Object.assign(Object.assign({},s),{limit:this.pageSize+1})).then(e=>{this.loading$.next(!1),this.eventBuffer.reset(),this.blockHasMore=!1,this.eventBuffer.addArchiveData(e)})}hasMore(){return null!=this.offscreenRecord&&!this.blockHasMore}loadPage(s){return this.options=s,this.yamcs.yamcsClient.getEvents(this.yamcs.instance,s).then(e=>(e.length>this.pageSize?(e.splice(e.length-1,1),this.offscreenRecord=e[e.length-1]):this.offscreenRecord=null,e))}loadMoreData(s){!this.offscreenRecord||this.loadPage(Object.assign(Object.assign({},s),{stop:this.offscreenRecord.generationTime,limit:this.pageSize+1})).then(e=>{this.eventBuffer.addArchiveData(e)})}startStreaming(){this.streaming$.next(!0),this.realtimeSubscription=this.yamcs.yamcsClient.createEventSubscription({instance:this.yamcs.instance},s=>{!this.loading$.getValue()&&this.matchesFilter(s)&&(s.animate=!0,this.eventBuffer.addRealtimeEvent(s))})}matchesFilter(s){if(this.options){if(this.options.source&&s.source!==this.options.source||this.options.q&&-1===s.message.indexOf(this.options.q))return!1;if(this.options.severity)switch(this.options.severity){case"SEVERE":case"ERROR":if("CRITICAL"===s.severity)return!1;case"CRITICAL":if("DISTRESS"===s.severity)return!1;case"DISTRESS":if("WARNING"===s.severity)return!1;case"WARNING":if("WATCH"===s.severity)return!1;case"WATCH":if("INFO"===s.severity)return!1}}return!0}stopStreaming(){this.realtimeSubscription&&this.realtimeSubscription.cancel(),this.streaming$.next(!1)}disconnect(){this.stopStreaming(),this.syncSubscription&&this.syncSubscription.unsubscribe(),this.events$.complete(),this.loading$.complete(),this.streaming$.complete()}}var R=a(43),p=a(9808);let J=(()=>{class i{constructor(e,n,r){this.dialogRef=e,this.yamcs=n,this.data=r,this.delimiterOptions=[{id:"COMMA",label:"Comma"},{id:"SEMICOLON",label:"Semicolon"},{id:"TAB",label:"Tab"}],this.downloadURL$=new m.X(null),this.form=new o.cw({start:new o.NI(null),stop:new o.NI(null),severity:new o.NI(null,o.kI.required),q:new o.NI(null),source:new o.NI(null),delimiter:new o.NI(null,o.kI.required)}),this.form.setValue({start:r.start?c.Y6(r.start):"",stop:r.stop?c.Y6(r.stop):"",q:r.q||"",source:r.source||"",severity:r.severity,delimiter:"TAB"}),this.formChangeSubscription=this.form.valueChanges.subscribe(()=>{this.updateURL()}),this.updateURL()}closeDialog(){this.dialogRef.close(!0)}updateURL(){if(this.form.valid){const e={delimiter:this.form.value.delimiter,severity:this.form.value.severity};if(this.form.value.start&&(e.start=c.Y6(this.form.value.start)),this.form.value.stop&&(e.stop=c.Y6(this.form.value.stop)),this.form.value.q&&(e.q=this.form.value.q),this.form.value.source){const r=this.form.value.source;e.source="ANY"!==r?r:null}const n=this.yamcs.yamcsClient.getEventsDownloadURL(this.yamcs.instance,e);this.downloadURL$.next(n)}else this.downloadURL$.next(null)}ngOnDestroy(){this.formChangeSubscription&&this.formChangeSubscription.unsubscribe()}}return i.\u0275fac=function(e){return new(e||i)(t.Y36(u.so),t.Y36(T.C),t.Y36(u.WI))},i.\u0275cmp=t.Xpm({type:i,selectors:[["ng-component"]],decls:36,vars:9,consts:[["mat-dialog-title",""],[1,"mat-typography","ya-form"],[3,"formGroup"],["formControlName","start"],["formControlName","stop"],["formControlName","severity",3,"options"],["type","text","formControlName","q"],["formControlName","source",3,"options"],["formControlName","delimiter",3,"options"],["align","end"],["mat-dialog-close","",1,"ya-button"],[3,"link","primary","disabled","click"]],template:function(e,n){1&e&&(t.TgZ(0,"h2",0),t._uU(1,"Export events"),t.qZA(),t.TgZ(2,"mat-dialog-content",1),t.TgZ(3,"form",2),t.TgZ(4,"label"),t._uU(5," Start "),t._UZ(6,"app-date-time-input",3),t.qZA(),t._UZ(7,"br"),t.TgZ(8,"label"),t._uU(9," Stop "),t._UZ(10,"app-date-time-input",4),t.qZA(),t._UZ(11,"br"),t.TgZ(12,"label"),t._uU(13," Severity"),t._UZ(14,"br"),t._UZ(15,"app-select",5),t.qZA(),t._UZ(16,"br"),t.TgZ(17,"label"),t._uU(18," Text filter "),t._UZ(19,"input",6),t.qZA(),t._UZ(20,"br"),t.TgZ(21,"label"),t._uU(22," Source"),t._UZ(23,"br"),t._UZ(24,"app-select",7),t.qZA(),t._UZ(25,"br"),t.TgZ(26,"label"),t._uU(27," CSV column delimiter"),t._UZ(28,"br"),t._UZ(29,"app-select",8),t.qZA(),t.qZA(),t.qZA(),t.TgZ(30,"mat-dialog-actions",9),t.TgZ(31,"button",10),t._uU(32,"CANCEL"),t.qZA(),t.TgZ(33,"app-download-button",11),t.NdJ("click",function(){return n.closeDialog()}),t.ALo(34,"async"),t._uU(35," DOWNLOAD "),t.qZA(),t.qZA()),2&e&&(t.xp6(3),t.Q6J("formGroup",n.form),t.xp6(12),t.Q6J("options",n.data.severityOptions),t.xp6(9),t.Q6J("options",n.data.sourceOptions),t.xp6(5),t.Q6J("options",n.delimiterOptions),t.xp6(4),t.Q6J("link",t.lcZ(34,7,n.downloadURL$))("primary",!0)("disabled",!n.form.valid))},directives:[u.uh,u.xY,o._Y,o.JL,o.sg,S.Y,o.JJ,o.u,y.P,o.Fj,u.H8,u.ZT,R.o],pipes:[p.Ov],encapsulation:2}),i})();var k=a(2313),Q=a(5328),F=a(8928),L=a(9140),$=a(7423),B=a(3874),H=a(7238),j=a(9971),V=a(4574),W=a(729),G=a(1711),v=a(8279),g=a(7587);function z(i,s){if(1&i&&t._UZ(0,"div",8),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}function X(i,s){if(1&i&&t._UZ(0,"div",9),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}function K(i,s){if(1&i&&t._UZ(0,"div",10),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}function tt(i,s){if(1&i&&t._UZ(0,"div",11),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}function et(i,s){if(1&i&&t._UZ(0,"div",12),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}function it(i,s){if(1&i&&t._UZ(0,"div",13),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}function st(i,s){if(1&i&&t._UZ(0,"div",14),2&i){const e=t.oxw();t.ekj("grayscale",e.grayscale)}}let nt=(()=>{class i{constructor(){this.grayscale=!1}}return i.\u0275fac=function(e){return new(e||i)},i.\u0275cmp=t.Xpm({type:i,selectors:[["app-event-severity"]],inputs:{severity:"severity",grayscale:"grayscale"},decls:8,vars:8,consts:[[3,"ngSwitch"],["class","info",3,"grayscale",4,"ngSwitchCase"],["class","watch",3,"grayscale",4,"ngSwitchCase"],["class","warning",3,"grayscale",4,"ngSwitchCase"],["class","distress",3,"grayscale",4,"ngSwitchCase"],["class","critical",3,"grayscale",4,"ngSwitchCase"],["class","severe",3,"grayscale",4,"ngSwitchCase"],["class","error",3,"grayscale",4,"ngSwitchCase"],[1,"info"],[1,"watch"],[1,"warning"],[1,"distress"],[1,"critical"],[1,"severe"],[1,"error"]],template:function(e,n){1&e&&(t.ynx(0,0),t.YNc(1,z,1,2,"div",1),t.YNc(2,X,1,2,"div",2),t.YNc(3,K,1,2,"div",3),t.YNc(4,tt,1,2,"div",4),t.YNc(5,et,1,2,"div",5),t.YNc(6,it,1,2,"div",6),t.YNc(7,st,1,2,"div",7),t.BQk()),2&e&&(t.Q6J("ngSwitch",n.severity),t.xp6(1),t.Q6J("ngSwitchCase","INFO"),t.xp6(1),t.Q6J("ngSwitchCase","WATCH"),t.xp6(1),t.Q6J("ngSwitchCase","WARNING"),t.xp6(1),t.Q6J("ngSwitchCase","DISTRESS"),t.xp6(1),t.Q6J("ngSwitchCase","CRITICAL"),t.xp6(1),t.Q6J("ngSwitchCase","SEVERE"),t.xp6(1),t.Q6J("ngSwitchCase","ERROR"))},directives:[p.RF,p.n9],styles:[".info[_ngcontent-%COMP%], .watch[_ngcontent-%COMP%], .warning[_ngcontent-%COMP%], .distress[_ngcontent-%COMP%], .critical[_ngcontent-%COMP%], .severe[_ngcontent-%COMP%], .error[_ngcontent-%COMP%]{display:inline-block;height:5px;width:29px;position:relative;background-image:url(level-sprite.png)}.info[_ngcontent-%COMP%]{background-position:0 0}.watch[_ngcontent-%COMP%]{background-position:0 -5px}.warning[_ngcontent-%COMP%]{background-position:0 -10px}.distress[_ngcontent-%COMP%]{background-position:0 -15px}.critical[_ngcontent-%COMP%]{background-position:0 -20px}.severe[_ngcontent-%COMP%], .error[_ngcontent-%COMP%]{background-position:0 -25px}.grayscale[_ngcontent-%COMP%]{filter:grayscale(100%);-webkit-filter:grayscale(100%)}@media print{.info[_ngcontent-%COMP%], .watch[_ngcontent-%COMP%], .warning[_ngcontent-%COMP%], .distress[_ngcontent-%COMP%], .critical[_ngcontent-%COMP%], .severe[_ngcontent-%COMP%], .error[_ngcontent-%COMP%]{-webkit-print-color-adjust:exact;color-adjust:exact}}"],changeDetection:0}),i})();var at=a(8726),ot=a(4594),rt=a(119);const lt=["intervalSelect"];function ct(i,s){if(1&i){const e=t.EpF();t.TgZ(0,"button",4),t.NdJ("click",function(){return t.CHM(e),t.oxw().createEvent()}),t.TgZ(1,"mat-icon"),t._uU(2,"add_circle_outline"),t.qZA(),t._uU(3," CREATE EVENT "),t.qZA()}}function ut(i,s){if(1&i){const e=t.EpF();t.TgZ(0,"button",4),t.NdJ("click",function(){return t.CHM(e),t.oxw().startStreaming()}),t.TgZ(1,"mat-icon"),t._uU(2,"play_arrow"),t.qZA(),t._uU(3," START STREAMING "),t.qZA()}}function pt(i,s){if(1&i){const e=t.EpF();t.TgZ(0,"button",5),t.NdJ("click",function(){return t.CHM(e),t.oxw().stopStreaming()}),t.TgZ(1,"mat-icon"),t._uU(2,"pause"),t.qZA(),t._uU(3," STOP STREAMING "),t.qZA()}}function ht(i,s){if(1&i){const e=t.EpF();t.ynx(0),t._UZ(1,"app-date-time-input",39),t._UZ(2,"app-date-time-input",40),t.TgZ(3,"button",38),t.NdJ("click",function(){return t.CHM(e),t.oxw(2).applyCustomDates()}),t._uU(4,"Apply"),t.qZA(),t.BQk()}if(2&i){const e=t.oxw(2);t.xp6(3),t.Q6J("disabled",e.filterForm.invalid)}}function mt(i,s){if(1&i){const e=t.EpF();t.ynx(0),t.TgZ(1,"button",41),t.NdJ("click",function(){return t.CHM(e),t.oxw(2).jumpToNow()}),t._uU(2," Jump to now "),t.qZA(),t.BQk()}}function dt(i,s){1&i&&t._UZ(0,"app-dots")}function vt(i,s){1&i&&(t.TgZ(0,"div",42),t._uU(1," Listening for events "),t._UZ(2,"app-dots",43),t.qZA())}function gt(i,s){if(1&i&&(t.TgZ(0,"span"),t._uU(1," Showing events from "),t.TgZ(2,"b"),t._uU(3,"the last hour"),t.qZA(),t._uU(4," ending at "),t.TgZ(5,"b"),t._uU(6),t.ALo(7,"datetime"),t.qZA(),t._uU(8," (Mission Time) "),t.qZA()),2&i){const e=t.oxw(2);t.xp6(6),t.Oqu(t.lcZ(7,1,e.validStop))}}function ft(i,s){if(1&i&&(t.TgZ(0,"span"),t._uU(1," Showing events from "),t.TgZ(2,"b"),t._uU(3,"the last 6 hours"),t.qZA(),t._uU(4," ending at "),t.TgZ(5,"b"),t._uU(6),t.ALo(7,"datetime"),t.qZA(),t._uU(8," (Mission Time) "),t.qZA()),2&i){const e=t.oxw(2);t.xp6(6),t.Oqu(t.lcZ(7,1,e.validStop))}}function _t(i,s){if(1&i&&(t.TgZ(0,"span"),t._uU(1," Showing events from "),t.TgZ(2,"b"),t._uU(3,"the last 24 hours"),t.qZA(),t._uU(4," ending at "),t.TgZ(5,"b"),t._uU(6),t.ALo(7,"datetime"),t.qZA(),t._uU(8," (Mission Time) "),t.qZA()),2&i){const e=t.oxw(2);t.xp6(6),t.Oqu(t.lcZ(7,1,e.validStop))}}function Tt(i,s){1&i&&(t.TgZ(0,"span"),t._uU(1," Showing events from "),t.TgZ(2,"b"),t._uU(3,"all time"),t.qZA(),t.qZA())}function yt(i,s){if(1&i&&(t.TgZ(0,"span"),t._uU(1," Showing events from "),t.TgZ(2,"b"),t._uU(3),t.ALo(4,"datetime"),t.qZA(),t._uU(5," to "),t.TgZ(6,"b"),t._uU(7),t.ALo(8,"datetime"),t.qZA(),t._uU(9," (Mission Time) "),t.qZA()),2&i){const e=t.oxw(2);t.xp6(3),t.Oqu(t.lcZ(4,2,e.validStart)),t.xp6(4),t.Oqu(t.lcZ(8,4,e.validStop))}}function St(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Severity"),t.qZA())}function Zt(i,s){if(1&i&&(t.TgZ(0,"td",45),t._UZ(1,"app-event-severity",46),t.qZA()),2&i){const e=s.$implicit;t.xp6(1),t.Q6J("severity",e.severity)}}function xt(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Message"),t.qZA())}function Ct(i,s){if(1&i&&(t.TgZ(0,"td",47),t._UZ(1,"app-highlight",48),t.qZA()),2&i){const e=s.$implicit,n=t.oxw(2);t.xp6(1),t.Q6J("text",e.message||"-")("term",n.filterForm.value.filter)}}function Et(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Type"),t.qZA())}function bt(i,s){if(1&i&&(t.TgZ(0,"td",45),t._uU(1),t.qZA()),2&i){const e=s.$implicit;t.xp6(1),t.hij(" ",e.type||"-"," ")}}function At(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Source"),t.qZA())}function Ut(i,s){if(1&i&&(t.TgZ(0,"td",45),t._uU(1),t.qZA()),2&i){const e=s.$implicit;t.xp6(1),t.hij(" ",e.source||"-"," ")}}function Nt(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Generation Time"),t.qZA())}function wt(i,s){if(1&i&&(t.TgZ(0,"td",49),t._uU(1),t.ALo(2,"datetime"),t.qZA()),2&i){const e=s.$implicit;t.xp6(1),t.hij(" ",t.lcZ(2,1,e.generationTime)||"-"," ")}}function Ot(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Reception Time"),t.qZA())}function It(i,s){if(1&i&&(t.TgZ(0,"td",49),t._uU(1),t.ALo(2,"datetime"),t.qZA()),2&i){const e=s.$implicit;t.xp6(1),t.hij(" ",t.lcZ(2,1,e.receptionTime)||"-"," ")}}function Pt(i,s){1&i&&(t.TgZ(0,"th",44),t._uU(1,"Sequence Number"),t.qZA())}function Mt(i,s){if(1&i&&(t.TgZ(0,"td",45),t._uU(1),t.qZA()),2&i){const e=s.$implicit;t.xp6(1),t.hij(" ",e.seqNumber||"-"," ")}}function Yt(i,s){if(1&i&&(t.TgZ(0,"th",44),t._uU(1),t.qZA()),2&i){const e=t.oxw().$implicit;t.Udp("width",e.width||"200px"),t.xp6(1),t.Oqu(e.label)}}function Dt(i,s){if(1&i&&(t.TgZ(0,"td",45),t._uU(1),t.qZA()),2&i){const e=s.$implicit,n=t.oxw().$implicit;t.xp6(1),t.hij(" ",e[n.id]||"-"," ")}}function qt(i,s){1&i&&(t.ynx(0,50),t.YNc(1,Yt,2,3,"th",51),t.YNc(2,Dt,2,1,"td",25),t.BQk()),2&i&&t.Q6J("cdkColumnDef",s.$implicit.id)}function Rt(i,s){1&i&&t._UZ(0,"tr",52)}function Jt(i,s){if(1&i&&t._UZ(0,"tr",53),2&i){const e=s.$implicit;t.Q6J("@rowAnimation",e.animate)("ngClass",e.severity)}}function kt(i,s){if(1&i){const e=t.EpF();t.TgZ(0,"div",6),t.TgZ(1,"form",7),t.TgZ(2,"div",8),t._UZ(3,"app-search-filter",9),t._UZ(4,"app-column-chooser",10,11),t.qZA(),t.TgZ(6,"div",8),t._UZ(7,"app-select",12),t._UZ(8,"app-select",13),t.ALo(9,"async"),t._UZ(10,"app-select",14,15),t.YNc(12,ht,5,1,"ng-container",16),t.YNc(13,mt,3,0,"ng-container",16),t.YNc(14,dt,1,0,"app-dots",16),t.ALo(15,"async"),t.YNc(16,vt,3,0,"div",17),t.ALo(17,"async"),t.qZA(),t.qZA(),t.TgZ(18,"div",18),t.TgZ(19,"div",19),t.TgZ(20,"app-text-action",20),t.NdJ("click",function(){return t.CHM(e),t.oxw().exportEvents()}),t._uU(21,"Download events"),t.qZA(),t.qZA(),t.YNc(22,gt,9,3,"span",21),t.YNc(23,ft,9,3,"span",21),t.YNc(24,_t,9,3,"span",21),t.YNc(25,Tt,4,0,"span",21),t.YNc(26,yt,10,6,"span",21),t.qZA(),t.TgZ(27,"table",22),t.ynx(28,23),t.YNc(29,St,2,0,"th",24),t.YNc(30,Zt,2,1,"td",25),t.BQk(),t.ynx(31,26),t.YNc(32,xt,2,0,"th",24),t.YNc(33,Ct,2,2,"td",27),t.BQk(),t.ynx(34,28),t.YNc(35,Et,2,0,"th",24),t.YNc(36,bt,2,1,"td",25),t.BQk(),t.ynx(37,29),t.YNc(38,At,2,0,"th",24),t.YNc(39,Ut,2,1,"td",25),t.BQk(),t.ynx(40,30),t.YNc(41,Nt,2,0,"th",24),t.YNc(42,wt,3,3,"td",31),t.BQk(),t.ynx(43,32),t.YNc(44,Ot,2,0,"th",24),t.YNc(45,It,3,3,"td",31),t.BQk(),t.ynx(46,33),t.YNc(47,Pt,2,0,"th",24),t.YNc(48,Mt,2,1,"td",25),t.BQk(),t.YNc(49,qt,3,1,"ng-container",34),t.YNc(50,Rt,1,0,"tr",35),t.ALo(51,"async"),t.YNc(52,Jt,1,2,"tr",36),t.ALo(53,"async"),t.qZA(),t.TgZ(54,"mat-toolbar"),t._UZ(55,"span",37),t.TgZ(56,"button",38),t.NdJ("click",function(){return t.CHM(e),t.oxw().loadMoreData()}),t._uU(57,"Load More"),t.qZA(),t._UZ(58,"span",37),t.qZA(),t.qZA()}if(2&i){const e=t.MAs(5),n=t.oxw();t.xp6(1),t.Q6J("formGroup",n.filterForm),t.xp6(3),t.Q6J("columns",n.columns),t.xp6(3),t.Q6J("options",n.severityOptions),t.xp6(1),t.Q6J("options",t.lcZ(9,20,n.sourceOptions$)),t.xp6(2),t.Q6J("options",n.intervalOptions),t.xp6(2),t.Q6J("ngIf","CUSTOM"===n.filterForm.value.interval),t.xp6(1),t.Q6J("ngIf","CUSTOM"!==n.filterForm.value.interval),t.xp6(1),t.Q6J("ngIf",t.lcZ(15,22,n.dataSource.loading$)),t.xp6(2),t.Q6J("ngIf",t.lcZ(17,24,n.dataSource.streaming$)),t.xp6(2),t.Q6J("ngSwitch",n.appliedInterval),t.xp6(4),t.Q6J("ngSwitchCase","PT1H"),t.xp6(1),t.Q6J("ngSwitchCase","PT6H"),t.xp6(1),t.Q6J("ngSwitchCase","P1D"),t.xp6(1),t.Q6J("ngSwitchCase","NO_LIMIT"),t.xp6(1),t.Q6J("ngSwitchCase","CUSTOM"),t.xp6(1),t.Q6J("dataSource",n.dataSource),t.xp6(22),t.Q6J("ngForOf",n.extraColumns),t.xp6(1),t.Q6J("cdkHeaderRowDef",t.lcZ(51,26,e.displayedColumns$)),t.xp6(2),t.Q6J("cdkRowDefColumns",t.lcZ(53,28,e.displayedColumns$)),t.xp6(4),t.Q6J("disabled",!n.dataSource.hasMore())}}const f="PT1H";const Qt=[{path:"",canActivate:[x.a,A.z],canActivateChild:[x.a],runGuardsAndResolvers:"always",component:N.K,children:[{path:"",pathMatch:"full",component:(()=>{class i{constructor(e,n,r,d,$t,Bt,Ht,jt){this.yamcs=e,this.authService=n,this.dialog=r,this.router=$t,this.route=Bt,this.filterForm=new o.cw({filter:new o.NI,severity:new o.NI("INFO"),source:new o.NI("ANY"),interval:new o.NI(f),customStart:new o.NI(null),customStop:new o.NI(null)}),this.columns=[{id:"severity",label:"Severity",visible:!0},{id:"gentime",label:"Generation Time",alwaysVisible:!0},{id:"message",label:"Message",alwaysVisible:!0},{id:"source",label:"Source",visible:!0},{id:"type",label:"Type",visible:!0},{id:"rectime",label:"Reception Time"},{id:"seqNumber",label:"Sequence Number"}],this.extraColumns=[],this.severityOptions=[{id:"INFO",label:"Info level"},{id:"WATCH",label:"Watch level"},{id:"WARNING",label:"Warning level"},{id:"DISTRESS",label:"Distress level"},{id:"CRITICAL",label:"Critical level"},{id:"SEVERE",label:"Severe level"}],this.sourceOptions$=new m.X([{id:"ANY",label:"Any source"}]),this.intervalOptions=[{id:"PT1H",label:"Last hour"},{id:"PT6H",label:"Last 6 hours"},{id:"P1D",label:"Last 24 hours"},{id:"NO_LIMIT",label:"No limit"},{id:"CUSTOM",label:"Custom",group:!0}],this.downloadURL$=new m.X(null),this.severity="INFO",Ht.setTitle("Events");const E=d.getConfig().events;if(E){this.extraColumns=E.extraColumns||[];for(const l of this.extraColumns)for(let h=0;h<this.columns.length;h++)if(this.columns[h].id===l.after){this.columns.splice(h+1,0,l);break}}e.yamcsClient.getEventSources(e.instance).then(l=>{for(const h of l)this.sourceOptions$.next([...this.sourceOptions$.value,{id:h,label:h}])}),this.dataSource=new q(e,jt),this.initializeOptions(),this.loadData(),this.filterForm.get("filter").valueChanges.pipe((0,w.b)(400)).forEach(l=>{this.filter=l,this.loadData()}),this.filterForm.get("severity").valueChanges.forEach(l=>{this.severity=l,this.loadData()}),this.filterForm.get("source").valueChanges.forEach(l=>{this.source="ANY"!==l?l:null,this.loadData()}),this.filterForm.get("interval").valueChanges.forEach(l=>{if("CUSTOM"===l){const h=this.validStart||this.yamcs.getMissionTime(),Vt=this.validStop||this.yamcs.getMissionTime();this.filterForm.get("customStart").setValue(c.Y6(h)),this.filterForm.get("customStop").setValue(c.Y6(Vt))}else"NO_LIMIT"===l?(this.validStart=null,this.validStop=null,this.appliedInterval=l,this.loadData()):(this.validStop=e.getMissionTime(),this.validStart=(0,c.rV)(this.validStop,l),this.appliedInterval=l,this.loadData())})}initializeOptions(){const e=this.route.snapshot.queryParamMap;if(e.has("filter")&&(this.filter=e.get("filter")||"",this.filterForm.get("filter").setValue(this.filter)),e.has("severity")&&(this.severity=e.get("severity"),this.filterForm.get("severity").setValue(this.severity)),e.has("source")&&(this.source=e.get("source"),this.filterForm.get("source").setValue(this.source)),e.has("interval"))if(this.appliedInterval=e.get("interval"),this.filterForm.get("interval").setValue(this.appliedInterval),"CUSTOM"===this.appliedInterval){const n=e.get("customStart");this.filterForm.get("customStart").setValue(n),this.validStart=c.ZU(n);const r=e.get("customStop");this.filterForm.get("customStop").setValue(r),this.validStop=c.ZU(r)}else"NO_LIMIT"===this.appliedInterval?(this.validStart=null,this.validStop=null):(this.validStop=this.yamcs.getMissionTime(),this.validStart=(0,c.rV)(this.validStop,this.appliedInterval));else this.appliedInterval=f,this.validStop=this.yamcs.getMissionTime(),this.validStart=(0,c.rV)(this.validStop,f)}jumpToNow(){const e=this.filterForm.value.interval;"NO_LIMIT"===e||"CUSTOM"===e?this.filterForm.get("interval").setValue(f):(this.validStop=this.yamcs.getMissionTime(),this.validStart=(0,c.rV)(this.validStop,e),this.loadData())}startStreaming(){this.filterForm.get("interval").setValue("NO_LIMIT"),this.dataSource.startStreaming()}stopStreaming(){this.dataSource.stopStreaming()}applyCustomDates(){this.validStart=c.ZU(this.filterForm.value.customStart),this.validStop=c.ZU(this.filterForm.value.customStop),this.appliedInterval="CUSTOM",this.loadData()}loadData(){this.updateURL();const e={severity:this.severity};this.validStart&&(e.start=this.validStart.toISOString()),this.validStop&&(e.stop=this.validStop.toISOString()),this.filter&&(e.q=this.filter),this.source&&(e.source=this.source),this.dataSource.loadEvents(e)}loadMoreData(){const e={severity:this.severity};this.validStart&&(e.start=this.validStart.toISOString()),this.filter&&(e.q=this.filter),this.source&&(e.source=this.source),this.dataSource.loadMoreData(e)}updateURL(){this.router.navigate([],{replaceUrl:!0,relativeTo:this.route,queryParams:{filter:this.filter||null,severity:this.severity,source:this.source||null,interval:this.appliedInterval,customStart:"CUSTOM"===this.appliedInterval?this.filterForm.value.customStart:null,customStop:"CUSTOM"===this.appliedInterval?this.filterForm.value.customStop:null},queryParamsHandling:"merge"})}mayWriteEvents(){return this.authService.getUser().hasSystemPrivilege("WriteEvents")}createEvent(){this.dialog.open(M,{width:"400px"}).afterClosed().subscribe(n=>{n&&this.jumpToNow()})}exportEvents(){this.dialog.open(J,{width:"400px",data:{severity:this.severity,severityOptions:this.severityOptions,start:this.validStart,stop:this.validStop,q:this.filter,source:this.source||"ANY",sourceOptions:this.sourceOptions$.value}})}}return i.\u0275fac=function(e){return new(e||i)(t.Y36(T.C),t.Y36(I.e),t.Y36(u.uw),t.Y36(P.E),t.Y36(_.F0),t.Y36(_.gz),t.Y36(k.Dx),t.Y36(Q.j))},i.\u0275cmp=t.Xpm({type:i,selectors:[["ng-component"]],viewQuery:function(e,n){if(1&e&&t.Gf(lt,5),2&e){let r;t.iGM(r=t.CRH())&&(n.intervalSelect=r.first)}},decls:12,vars:8,consts:[["mat-button","","color","primary",3,"click",4,"ngIf"],["mat-button","","matTooltip","Pause streaming events","color","primary",3,"click",4,"ngIf"],["mat-icon-button","","matTooltip","Jump to now","color","primary",3,"click"],["class","panel-content",4,"ngIf"],["mat-button","","color","primary",3,"click"],["mat-button","","matTooltip","Pause streaming events","color","primary",3,"click"],[1,"panel-content"],[3,"formGroup"],[1,"filter-bar"],["formControlName","filter","placeholder","Filter by text search"],["preferenceKey","events",3,"columns"],["columnChooser",""],["formControlName","severity",3,"options"],["formControlName","source",3,"options"],["icon","access_time","formControlName","interval",3,"options"],["intervalSelect",""],[4,"ngIf"],["style","text-align: right; flex: 1 1 150px",4,"ngIf"],[1,"table-status",3,"ngSwitch"],[1,"message-zone"],[3,"click"],[4,"ngSwitchCase"],["mat-table","",1,"ya-data-table","expand",3,"dataSource"],["cdkColumnDef","severity"],["mat-header-cell","",4,"cdkHeaderCellDef"],["mat-cell","",4,"cdkCellDef"],["cdkColumnDef","message"],["mat-cell","","class","mono","style","white-space: pre; width: 100%",4,"cdkCellDef"],["cdkColumnDef","type"],["cdkColumnDef","source"],["cdkColumnDef","gentime"],["mat-cell","","style","white-space: nowrap",4,"cdkCellDef"],["cdkColumnDef","rectime"],["cdkColumnDef","seqNumber"],[3,"cdkColumnDef",4,"ngFor","ngForOf"],["mat-header-row","",4,"cdkHeaderRowDef"],["mat-row","",3,"ngClass",4,"cdkRowDef","cdkRowDefColumns"],[2,"flex","1 1 auto"],[1,"ya-button",3,"disabled","click"],["formControlName","customStart"],["formControlName","customStop"],[1,"ya-button",3,"click"],[2,"text-align","right","flex","1 1 150px"],["fontSize","16px","color","#1b61b9"],["mat-header-cell",""],["mat-cell",""],[3,"severity"],["mat-cell","",1,"mono",2,"white-space","pre","width","100%"],[3,"text","term"],["mat-cell","",2,"white-space","nowrap"],[3,"cdkColumnDef"],["mat-header-cell","",3,"width",4,"cdkHeaderCellDef"],["mat-header-row",""],["mat-row","",3,"ngClass"]],template:function(e,n){1&e&&(t.TgZ(0,"app-instance-page"),t.TgZ(1,"app-instance-toolbar"),t._uU(2," Events \xa0\xa0\xa0 "),t.YNc(3,ct,4,0,"button",0),t.YNc(4,ut,4,0,"button",0),t.ALo(5,"async"),t.YNc(6,pt,4,0,"button",1),t.ALo(7,"async"),t.TgZ(8,"button",2),t.NdJ("click",function(){return n.jumpToNow()}),t.TgZ(9,"mat-icon"),t._uU(10,"refresh"),t.qZA(),t.qZA(),t.qZA(),t.YNc(11,kt,59,30,"div",3),t.qZA()),2&e&&(t.xp6(3),t.Q6J("ngIf",n.mayWriteEvents()),t.xp6(1),t.Q6J("ngIf",!t.lcZ(5,4,n.dataSource.streaming$)),t.xp6(2),t.Q6J("ngIf",t.lcZ(7,6,n.dataSource.streaming$)),t.xp6(5),t.Q6J("ngIf",n.dataSource))},directives:[F.a,L.k,p.O5,$.lW,B.Hw,H.gM,o._Y,o.JL,o.sg,j.a,o.JJ,o.u,V.k,y.P,S.Y,W.b,p.RF,G.l,p.n9,v.BZ,g.fo,g.D5,v.ge,g.O_,v.ev,nt,at.y,p.sg,g.s$,v.XQ,g.Sq,v.Gk,p.mk,ot.Ye],pipes:[p.Ov,rt.i],styles:[".table-status[_ngcontent-%COMP%]{background-color:#f7f7f7;height:24px;line-height:24px;font-size:12px;padding-left:6px}.table-status[_ngcontent-%COMP%]   .message-zone[_ngcontent-%COMP%]{float:right;height:24px;line-height:24px;font-size:12px;padding-right:6px}"],data:{animation:[O.q]},changeDetection:0}),i})(),canActivate:[U.T]}]}];let Ft=(()=>{class i{}return i.\u0275fac=function(e){return new(e||i)},i.\u0275mod=t.oAB({type:i}),i.\u0275inj=t.cJS({imports:[[_.Bz.forChild(Qt)],_.Bz]}),i})(),Lt=(()=>{class i{}return i.\u0275fac=function(e){return new(e||i)},i.\u0275mod=t.oAB({type:i}),i.\u0275inj=t.cJS({imports:[[b.m,Ft]]}),i})()}}]);