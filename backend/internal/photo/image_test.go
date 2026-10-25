package photo

import (
	"bytes"
	"encoding/binary"
	"image"
	"image/color"
	"image/gif"
	"image/jpeg"
	"image/png"
	"testing"
)

// halves : moitié gauche rouge, moitié droite bleue.
func halves(w, h int) *image.RGBA {
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			c := color.RGBA{220, 30, 30, 255}
			if x >= w/2 {
				c = color.RGBA{30, 30, 220, 255}
			}
			img.SetRGBA(x, y, c)
		}
	}
	return img
}

func encodeJPEG(t *testing.T, img image.Image) []byte {
	t.Helper()
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, &jpeg.Options{Quality: 95}); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

// withOrientation insère un segment EXIF (APP1) portant l'orientation o.
func withOrientation(data []byte, o uint16, bo binary.ByteOrder) []byte {
	tiff := make([]byte, 8+2+12+4)
	if bo == binary.LittleEndian {
		copy(tiff, "II")
	} else {
		copy(tiff, "MM")
	}
	bo.PutUint16(tiff[2:], 42)
	bo.PutUint32(tiff[4:], 8)
	bo.PutUint16(tiff[8:], 1)
	bo.PutUint16(tiff[10:], 0x0112)
	bo.PutUint16(tiff[12:], 3)
	bo.PutUint32(tiff[14:], 1)
	bo.PutUint16(tiff[18:], o)
	seg := append([]byte("Exif\x00\x00"), tiff...)
	app1 := []byte{0xFF, 0xE1, 0, 0}
	binary.BigEndian.PutUint16(app1[2:], uint16(len(seg)+2))
	out := append([]byte{}, data[:2]...)
	out = append(out, app1...)
	out = append(out, seg...)
	return append(out, data[2:]...)
}

func decode(t *testing.T, data []byte) image.Image {
	t.Helper()
	img, format, err := image.Decode(bytes.NewReader(data))
	if err != nil || format != "jpeg" {
		t.Fatalf("decode: %v (%s)", err, format)
	}
	return img
}

func isRed(c color.Color) bool {
	r, g, b, _ := c.RGBA()
	return r > 0x9000 && g < 0x6000 && b < 0x6000
}

func TestReadsExifOrientation(t *testing.T) {
	base := encodeJPEG(t, halves(8, 4))
	if got := orientation(base); got != 1 {
		t.Fatalf("no exif: got %d", got)
	}
	for _, bo := range []binary.ByteOrder{binary.LittleEndian, binary.BigEndian} {
		if got := orientation(withOrientation(base, 6, bo)); got != 6 {
			t.Fatalf("%v: got %d, want 6", bo, got)
		}
	}
	if got := orientation([]byte{0xFF, 0xD8, 0xFF, 0xE1, 0xFF}); got != 1 {
		t.Fatalf("truncated: got %d", got)
	}
}

func TestNormalizeTurnsThePhotoUpright(t *testing.T) {
	// Orientation 6 : à afficher tournée d'un quart de tour à droite. La moitié
	// gauche (rouge) doit se retrouver en haut.
	data := withOrientation(encodeJPEG(t, halves(80, 40)), 6, binary.LittleEndian)
	out, err := Normalize(data)
	if err != nil {
		t.Fatal(err)
	}
	img := decode(t, out)
	if b := img.Bounds(); b.Dx() != 40 || b.Dy() != 80 {
		t.Fatalf("size %v, want 40x80", b.Size())
	}
	if !isRed(img.At(20, 10)) || isRed(img.At(20, 70)) {
		t.Fatalf("not rotated: top %v, bottom %v", img.At(20, 10), img.At(20, 70))
	}
	if orientation(out) != 1 {
		t.Fatal("metadata kept")
	}
}

func TestNormalizeShrinksAndFlattens(t *testing.T) {
	big := image.NewNRGBA(image.Rect(0, 0, 3200, 1000)) // transparente
	var buf bytes.Buffer
	if err := png.Encode(&buf, big); err != nil {
		t.Fatal(err)
	}
	out, err := Normalize(buf.Bytes())
	if err != nil {
		t.Fatal(err)
	}
	img := decode(t, out)
	if b := img.Bounds(); b.Dx() != MaxSide || b.Dy() != 500 {
		t.Fatalf("size %v, want 1600x500", b.Size())
	}
	if r, g, b, _ := img.At(10, 10).RGBA(); r < 0xF000 || g < 0xF000 || b < 0xF000 {
		t.Fatalf("transparency not on white: %v", img.At(10, 10))
	}

	small := encodeJPEG(t, halves(300, 200))
	out, err = Normalize(small)
	if err != nil {
		t.Fatal(err)
	}
	if b := decode(t, out).Bounds(); b.Dx() != 300 || b.Dy() != 200 {
		t.Fatalf("small photo resized to %v", b.Size())
	}
}

func TestNormalizeRefusesOtherFiles(t *testing.T) {
	var buf bytes.Buffer
	if err := gif.Encode(&buf, image.NewPaletted(image.Rect(0, 0, 4, 4), color.Palette{color.Black}), nil); err != nil {
		t.Fatal(err)
	}
	for name, data := range map[string][]byte{"gif": buf.Bytes(), "text": []byte("<svg onload=alert(1)>"), "empty": nil} {
		if _, err := Normalize(data); err != ErrUnsupported {
			t.Errorf("%s: got %v, want ErrUnsupported", name, err)
		}
	}
}

func TestFit(t *testing.T) {
	cases := []struct{ w, h, ww, wh int }{{4000, 3000, 1600, 1200}, {3000, 4000, 1200, 1600}, {800, 600, 800, 600}, {10000, 3, 1600, 1}}
	for _, c := range cases {
		if w, h := fit(c.w, c.h, MaxSide); w != c.ww || h != c.wh {
			t.Errorf("fit(%d,%d) = %d,%d, want %d,%d", c.w, c.h, w, h, c.ww, c.wh)
		}
	}
}
