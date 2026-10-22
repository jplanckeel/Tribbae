//go:build previewcov

// Mesure de couverture des aperçus (images trouvées) sur une liste d'URL réelles :
//
//	PREVIEW_URLS=urls.txt go test -tags previewcov -run TestCoverage -v -timeout 30m ./internal/preview
package preview

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"sort"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestCoverage(t *testing.T) {
	path := os.Getenv("PREVIEW_URLS")
	if path == "" {
		t.Skip("PREVIEW_URLS non défini")
	}
	f, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	var urls []string
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		if u := strings.TrimSpace(sc.Text()); u != "" && !strings.HasPrefix(u, "#") {
			urls = append(urls, u)
		}
	}
	type row struct {
		url, img, title, err string
		n                    int
		took                 time.Duration
	}
	rows := make([]row, len(urls))
	sem := make(chan struct{}, 6)
	var wg sync.WaitGroup
	for i, u := range urls {
		wg.Add(1)
		go func() {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			start := time.Now()
			m, err := Fetch(context.Background(), u)
			r := row{url: u, took: time.Since(start)}
			if err != nil {
				r.err = err.Error()
			} else {
				r.img, r.title, r.n = m.Image, m.Title, len(m.Images)
			}
			rows[i] = r
		}()
	}
	wg.Wait()
	sort.SliceStable(rows, func(i, j int) bool { return (rows[i].img == "") && (rows[j].img != "") })
	ok := 0
	for _, r := range rows {
		status := "OK "
		if r.img == "" {
			status = "KO "
		} else {
			ok++
		}
		fmt.Printf("%s %5.1fs n=%-2d %-70.70s | %-40.40q | %.90s %s\n", status, r.took.Seconds(), r.n, r.url, r.title, r.img, r.err)
	}
	fmt.Printf("\nCOUVERTURE : %d/%d URL avec image (%.0f %%)\n", ok, len(rows), 100*float64(ok)/float64(len(rows)))
}
