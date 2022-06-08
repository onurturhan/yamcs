var Runnable = Java.type("java.lang.Runnable");
var Thread = Java.type("java.lang.Thread");

new Thread(new Runnable({
	run: function() {
		display.getWidget("Start_Button").setPropertyValue("visible", false);
		display.getWidget("Progress_Bar").setPropertyValue("visible", true);
		for (var i = 100; i > 0; i--) {
			if (!display.isActive()) {
				return;
			}
			if (i % 10 == 0) {
				widget.setPropertyValue("text", "I'm going to finish in " + i / 10 + " seconds...");
			}
			pvs[1].setValue(100 - i);
			Thread.sleep(100);
		}
		pvs[1].setValue(100);
		widget.setPropertyValue("text", "I'm done! Hit the button again to start me.");
		display.getWidget("Start_Button").setPropertyValue("visible", true);
		display.getWidget("Progress_Bar").setPropertyValue("visible", false);
	}
})).start();
