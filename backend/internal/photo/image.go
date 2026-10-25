package photo

import (
	"bytes"
	"encoding/binary"
	"errors"
	"image"
	"image/color"
	"image/jpeg"
	_ "image/png" // décodeur PNG
	"math"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp" // décodeur WebP
)

const (
	// MaxSide : plus grand côté gardé (largement assez pour une fiche plein écran).
	MaxSide = 1600
	// maxPixels refuse les images trop grandes à décoder (bombes de décompression).
	maxPixels = 50_000_000
	quality   = 82
)

var ErrUnsupported = errors.New("photo must be a jpeg, png or webp image")

// Normalize décode la photo envoyée, la redresse (orientation EXIF des
// appareils photo), la réduit à MaxSide et la réencode en JPEG. Le
// réencodage retire toutes les métadonnées (position GPS, appareil…) ; la
// transparence éventuelle est posée sur du blanc.
func Normalize(data []byte) ([]byte, error) {
	cfg, format, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return nil, ErrUnsupported
	}
	if format != "jpeg" && format != "png" && format != "webp" {
		return nil, ErrUnsupported
	}
	if cfg.Width <= 0 || cfg.Height <= 0 || cfg.Width*cfg.Height > maxPixels {
		return nil, ErrUnsupported
	}
	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, ErrUnsupported
	}

	w, h := fit(cfg.Width, cfg.Height, MaxSide)
	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	draw.Draw(dst, dst.Bounds(), image.NewUniform(color.White), image.Point{}, draw.Src)
	if w == cfg.Width && h == cfg.Height {
		draw.Draw(dst, dst.Bounds(), src, src.Bounds().Min, draw.Over)
	} else {
		draw.CatmullRom.Scale(dst, dst.Bounds(), src, src.Bounds(), draw.Over, nil)
	}
	out := dst
	if format == "jpeg" {
		out = orient(dst, orientation(data))
	}

	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, out, &jpeg.Options{Quality: quality}); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// fit réduit (w, h) pour que le plus grand côté tienne dans max, sans agrandir.
func fit(w, h, max int) (int, int) {
	if w <= max && h <= max {
		return w, h
	}
	if w >= h {
		return max, int(math.Max(1, math.Round(float64(h)*float64(max)/float64(w))))
	}
	return int(math.Max(1, math.Round(float64(w)*float64(max)/float64(h)))), max
}

// orientation lit l'étiquette EXIF Orientation (1 à 8) d'un JPEG, 1 si absente.
func orientation(data []byte) int {
	if len(data) < 4 || data[0] != 0xFF || data[1] != 0xD8 {
		return 1
	}
	for i := 2; i+4 <= len(data); {
		if data[i] != 0xFF {
			return 1
		}
		marker := data[i+1]
		if marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01 || marker == 0xFF {
			i += 2
			continue
		}
		if marker == 0xDA || marker == 0xD9 { // début de l'image : plus de métadonnées
			return 1
		}
		size := int(binary.BigEndian.Uint16(data[i+2:]))
		end := i + 2 + size
		if size < 2 || end > len(data) {
			return 1
		}
		if seg := data[i+4 : end]; marker == 0xE1 && len(seg) > 6 && string(seg[:6]) == "Exif\x00\x00" {
			return tiffOrientation(seg[6:])
		}
		i = end
	}
	return 1
}

func tiffOrientation(t []byte) int {
	if len(t) < 8 {
		return 1
	}
	var bo binary.ByteOrder
	switch string(t[:2]) {
	case "II":
		bo = binary.LittleEndian
	case "MM":
		bo = binary.BigEndian
	default:
		return 1
	}
	ifd := int(bo.Uint32(t[4:]))
	if ifd < 8 || ifd+2 > len(t) {
		return 1
	}
	n := int(bo.Uint16(t[ifd:]))
	for k := 0; k < n; k++ {
		e := ifd + 2 + 12*k
		if e+12 > len(t) {
			return 1
		}
		if bo.Uint16(t[e:]) == 0x0112 && bo.Uint16(t[e+2:]) == 3 { // Orientation, SHORT
			if v := int(bo.Uint16(t[e+8:])); v >= 1 && v <= 8 {
				return v
			}
			return 1
		}
	}
	return 1
}

// orient applique l'orientation EXIF o : l'image sort « à l'endroit ».
func orient(src *image.RGBA, o int) *image.RGBA {
	if o <= 1 || o > 8 {
		return src
	}
	w, h := src.Bounds().Dx(), src.Bounds().Dy()
	dw, dh := w, h
	if o >= 5 {
		dw, dh = h, w
	}
	dst := image.NewRGBA(image.Rect(0, 0, dw, dh))
	for y := 0; y < dh; y++ {
		for x := 0; x < dw; x++ {
			var sx, sy int
			switch o {
			case 2:
				sx, sy = w-1-x, y
			case 3:
				sx, sy = w-1-x, h-1-y
			case 4:
				sx, sy = x, h-1-y
			case 5:
				sx, sy = y, x
			case 6:
				sx, sy = y, h-1-x
			case 7:
				sx, sy = w-1-y, h-1-x
			case 8:
				sx, sy = w-1-y, x
			}
			si, di := src.PixOffset(sx, sy), dst.PixOffset(x, y)
			copy(dst.Pix[di:di+4], src.Pix[si:si+4])
		}
	}
	return dst
}
