package com.github.hey_world_team.currency_converter.service;

import com.github.hey_world_team.currency_converter.config.PropertiesForFileService;
import com.github.hey_world_team.currency_converter.model.Currency;
import com.github.hey_world_team.currency_converter.repository.CurrencyRepository;
import com.github.hey_world_team.currency_converter.service.status.DataBasePrepare;
import com.github.hey_world_team.currency_converter.service.status.FileWriteStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.parseDouble;

@Service
public class FileService {

    private static final String CURRENCY_TAG_NAME = "Valute";

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private final String fileForeignCurrencies;
    private final String charset;
    private final String link;
    private final PropertiesForFileService propertiesForFileService;
    private final CurrencyRepository currencyRepository;
    private final List<Currency> currencies;

    @Autowired
    public FileService(PropertiesForFileService propertiesForFileService,
                       CurrencyRepository currencyRepository) {
        this.propertiesForFileService = propertiesForFileService;
        this.fileForeignCurrencies = propertiesForFileService.getFileForeignCurrencies();
        this.charset = propertiesForFileService.getCharset();
        this.link = propertiesForFileService.getLink();
        this.currencyRepository = currencyRepository;
        this.currencies = new ArrayList<>();
    }

    public int prepareDataBase(DataBasePrepare status) {
        log.info("start prepare data base");
        int count = 0;
        var restTemplate = new RestTemplate();
        String currenciesXml = restTemplate.getForObject(link, String.class);
        log.info("download from {} current course", link);
        assert currenciesXml != null;
        String answer = writeToFile(currenciesXml);
        log.info("current course {}", answer);
        if (answer.equals(FileWriteStatus.WRITTEN.name())) {
            log.info("start parsing data to collection");
            parseXmlToCollectionObjects();
            log.info("end parsing data to  collection");
        }

        if (status.equals(DataBasePrepare.CREATE)) {
            log.info("data base is empty need to create new records");
            this.currencies.add(new Currency("RUB", "Российский рубль", new BigDecimal(1), 1));
            count = currencyRepository.saveCurrencies(currencies);
        } else if (status.equals(DataBasePrepare.UPDATE)){
            log.info("data base is not empty need to update values");
            count = currencyRepository.updateCurrencies(currencies);
        } else {
            throw new RuntimeException("Data base preparing status isn't understandable: " + status);
        }
        log.info("end prepare data base");
        return count;
    }

    /**
     * @param file
     * @return answer to controller
     */
    public String writeToFile(String file) {
        log.info("Started to read file {}", fileForeignCurrencies);
        var currencyFile = new File(propertiesForFileService.getPath() + "/" + fileForeignCurrencies);
        try (var outputStream = new FileOutputStream(currencyFile, false)) {
            log.info("Started to write file {}", fileForeignCurrencies);
            outputStream.write(file.getBytes());
        } catch (IOException ex) {
            log.error(ex.getMessage());
            return FileWriteStatus.NOT_WRITTEN.name();
        }
        log.info("Write {} completed", fileForeignCurrencies);
        return FileWriteStatus.WRITTEN.name();
    }

    /**
     *
     */
    public void parseXmlToCollectionObjects() {
        log.info("Started writing XML to object");
        var input = new File(propertiesForFileService.getPath() + "/" + fileForeignCurrencies);
        Document doc = null;
        try {
            doc = Jsoup.parse(input, charset, "", Parser.xmlParser());
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

        for (Element e : doc.select(CURRENCY_TAG_NAME)) {
            String id = e.getElementsByTag("CharCode").text();
            String name = e.getElementsByTag("Name").text();
            BigDecimal value = BigDecimal.valueOf(parseDouble(e.getElementsByTag("Value")
                    .text()
                    .replace(',', '.')));
            Integer nominal = Integer.valueOf(e.getElementsByTag("Nominal").text());
            Currency currency = new Currency(id, name, value, nominal);
            this.currencies.add(currency);
        }
    }
}
