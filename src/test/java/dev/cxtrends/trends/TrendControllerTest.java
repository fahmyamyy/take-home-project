package dev.cxtrends.trends;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Runs against the real data in data/, loaded into a test database. */
@SpringBootTest(properties = "cx.db-path=target/test-cx.db")
@AutoConfigureMockMvc
class TrendControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void flagsTheJulyOutageWeekAndLinksTheKnownIncident() throws Exception {
        mvc.perform(get("/trends").param("category", "Network Quality").param("region", "Sumatera Utara")
                        .param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.from").value("2026-06-29"))
                .andExpect(jsonPath("$.filters.to").value("2026-08-02"))
                .andExpect(jsonPath("$.weeks", hasSize(5)))
                .andExpect(jsonPath("$.weeks[2].weekStart").value("2026-07-13"))
                .andExpect(jsonPath("$.weeks[2].tickets").value(1309))
                .andExpect(jsonPath("$.weeks[2].anomaly").value(true))
                .andExpect(jsonPath("$.weeks[2].direction").value("UP"))
                .andExpect(jsonPath("$.weeks[2].knownIncidents[0].id").value("INC000001"))
                .andExpect(jsonPath("$.weeks[2].knownIncidents[0].relation").value("during"))
                // the week after: still elevated by follow-up contacts, and the API says why
                .andExpect(jsonPath("$.weeks[3].anomaly").value(true))
                .andExpect(jsonPath("$.weeks[3].knownIncidents[0].relation").value("previous_week"))
                .andExpect(jsonPath("$.weeks[4].anomaly").value(false))
                .andExpect(jsonPath("$.weeks[4].knownIncidents", hasSize(0)));
    }

    @Test
    void firstWeeksOfANarrowRangeStillGetABaseline() throws Exception {
        mvc.perform(get("/trends").param("from", "2026-08-10").param("to", "2026-08-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks", hasSize(1)))
                .andExpect(jsonPath("$.weeks[0].baseline").isNumber())
                .andExpect(jsonPath("$.weeks[0].anomaly").value(true));
    }

    @Test
    void matchesFilterValuesCaseInsensitively() throws Exception {
        mvc.perform(get("/trends").param("category", "app & digital"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.category").value("App & Digital"))
                .andExpect(jsonPath("$.weeks", hasSize(26)));
    }

    @Test
    void rejectsBadInput() throws Exception {
        mvc.perform(get("/trends").param("from", "13-07-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("YYYY-MM-DD")));
        mvc.perform(get("/trends").param("from", "2026-09-01").param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/trends").param("region", "Mars"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Jabodetabek")));
    }

    @Test
    void rangeOutsideTheDataIsEmptyNotAnError() throws Exception {
        mvc.perform(get("/trends").param("from", "2027-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks", hasSize(0)));
    }
}
