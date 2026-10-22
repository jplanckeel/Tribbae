package mail

import "strings"

// Device décrit en français l'appareil d'un User-Agent (« Firefox sur Linux »,
// « l'application Android »), pour l'email du mot de passe oublié. Vide si
// rien n'est reconnu.
func Device(userAgent string) string {
	ua := strings.ToLower(userAgent)
	if ua == "" {
		return ""
	}
	// L'application Android passe par HttpURLConnection (Dalvik)
	if strings.Contains(ua, "dalvik") || strings.Contains(ua, "okhttp") {
		return "l'application Android"
	}
	browser := ""
	switch {
	case strings.Contains(ua, "edg/"):
		browser = "Edge"
	case strings.Contains(ua, "opr/") || strings.Contains(ua, "opera"):
		browser = "Opera"
	case strings.Contains(ua, "samsungbrowser"):
		browser = "Samsung Internet"
	case strings.Contains(ua, "firefox/") || strings.Contains(ua, "fxios"):
		browser = "Firefox"
	case strings.Contains(ua, "chrome/") || strings.Contains(ua, "crios"):
		browser = "Chrome"
	case strings.Contains(ua, "safari/"):
		browser = "Safari"
	}
	system := ""
	switch {
	case strings.Contains(ua, "android"):
		system = "Android"
	case strings.Contains(ua, "iphone"):
		system = "iPhone"
	case strings.Contains(ua, "ipad"):
		system = "iPad"
	case strings.Contains(ua, "windows"):
		system = "Windows"
	case strings.Contains(ua, "mac os"):
		system = "Mac"
	case strings.Contains(ua, "cros"):
		system = "ChromeOS"
	case strings.Contains(ua, "linux"):
		system = "Linux"
	}
	switch {
	case browser != "" && system != "":
		return browser + " sur " + system
	case browser != "":
		return browser
	case system != "":
		return "un appareil " + system
	}
	return ""
}
