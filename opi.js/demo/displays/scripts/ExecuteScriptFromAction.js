var color;
var colorName;
if (Math.random() > 0.5) {
	color = ColorFontUtil.getColorFromRGB(0, 160, 0);
	colorName = "green";
} else {
	color = ColorFontUtil.RED;
	colorName = "red";
}
display.getWidget("myIndicator").setPropertyValue("background_color", color);
widget.setPropertyValue("foreground_color", color);
	
GUIUtil.openInformationDialog("JavaScript says: My color is " + colorName);
