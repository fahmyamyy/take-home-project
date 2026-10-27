package dev.cxtrends.classify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NoteNormalizerTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            sinyal hilang total sejak kemarin                                      | sinyal hilang total sejak kemarin | 0
            follow up dari laporan kemarin, sinyal hilang total sejak kemarin      | sinyal hilang total sejak kemarin | 1
            This is a follow up, app crashes immediately on opening                | app crashes immediately on opening | 1
            this is a follow up, still not resolved: kode otp tidak pernah masuk   | kode otp tidak pernah masuk | 2
            '  second time reporting the same issue, voucher ditolak  '            | voucher ditolak | 1
            di dalam rumah sinyal jelek, harus keluar dulu baru dapat              | di dalam rumah sinyal jelek, harus keluar dulu baru dapat | 0
            """)
    void stripsRepeatContactPrefixes(String note, String baseText, int depth) {
        NoteNormalizer.Normalized n = NoteNormalizer.normalize(note);

        assertThat(n.baseText()).isEqualTo(baseText);
        assertThat(n.followUpDepth()).isEqualTo(depth);
    }
}
