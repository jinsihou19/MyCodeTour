import org.junit.Assert;
import org.junit.Test;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.service.AppSettingsState;
import org.vito.mycodetour.tours.service.Utils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author vito
 * Created on 2025/1/1
 */
public class UtilsTests {

    @Test
    public void stringTest() {
        final String title = "Basket Items Issue Reproduce";
        final String expected = "basketItemsIssueReproduce.tour";
        final String actual = Utils.fileNameFromTitle(title);
        System.out.println(title);
        System.out.println(actual);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testMdToHtml() {
        final String md = "# Hey there\n\nHow are you **today**?";
        final String expectedHtml = "<body><h1>Hey there</h1><p>How are you <strong>today</strong>?</p></body>";

        final String html = Utils.mdToHtml(md, "");

        Assert.assertEquals(expectedHtml, html);
    }

    @Test
    public void testSort() {

        for (AppSettingsState.SortOptionE sortOption : AppSettingsState.SortOptionE.values()) {
            for (AppSettingsState.SortDirectionE sortDirection : AppSettingsState.SortDirectionE.values()) {
                List<Tour> tours = getTours();
                sort(tours, sortOption, sortDirection);
                System.out.printf("Results, after sorting using: %s - %s%n", sortOption, sortDirection);
                tours.forEach(tour -> System.out.printf("Title: %s, File: %s, Date: %s%n",
                        tour.getTitle(), tour.getTourFile(), tour.getCreatedAt()));
                System.out.println();
            }
        }
    }

    private void sort(List<Tour> tours, AppSettingsState.SortOptionE sortOption,
                      AppSettingsState.SortDirectionE sortDirection) {
        Comparator<Tour> comparator = Comparator.comparing(Tour::getTitle);
        switch (sortOption) {
            case FILENAME -> comparator = Comparator.comparing(Tour::getTourFile);
            case CREATION_DATE ->
                    comparator = Comparator.comparing(Tour::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (AppSettingsState.SortDirectionE.DESC.equals(sortDirection))
            comparator = comparator.reversed(); // ASC,DESC

        tours.sort(comparator);
    }

    private List<Tour> getTours() {
        List<Tour> tours = new ArrayList<>();
        for (int i = 1; i < 10; i++) {
            var tour = Tour.builder()
                    .title("Tour " + i)
                    .tourFile("tour_" + i + ".json")
                    .createdAt(i == 5 ? null : LocalDateTime.now().plusMinutes(i))
                    .build();
            tours.add(tour);
        }
        return tours;
    }
}