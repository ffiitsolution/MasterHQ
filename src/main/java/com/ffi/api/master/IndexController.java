package com.ffi.api.master;

import com.ffi.api.master.model.ResponseMessage;
import com.ffi.api.master.services.ProcessServices;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author IT
 */
@RestController
public class IndexController {

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    Constant constant;

    @Autowired
    ProcessServices processServices;

    @Value("${spring.datasource.url}")
    private String urlDb;

    @Value("${server.port}")
    String port;

    @Value("${server.servlet.context-path}")
    String path;

    @Value("${log.dir}")
    String currentDir;

    @RequestMapping(value = "/halo")
    public @ResponseBody
    Map<String, Object> tes() {
        Map<String, Object> map = new HashMap<>();
        map.put("output", "welcome to master");
//        map.put("urlDb", urlDb);
        map.put("port", port);
        map.put("path", path);
        map.put("versionBe", constant.versionBe);
        return map;
    }

    @RequestMapping(value = "/send-data-to-store", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Digunakan untuk generate template", response = Object.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 404, message = "The resource not found"),}
    )
    public @ResponseBody
    ResponseMessage insertUpdateMasterItem(@RequestBody String param) throws IOException, Exception {
        Gson gsn = new Gson();
        Map<String, String> balance = gsn.fromJson(param, new TypeToken<Map<String, Object>>() {
        }.getType());
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        String status = "";
        ResponseMessage rm = new ResponseMessage();
        try {
            processServices.insertUpdateMasterItem(balance);
            rm.setSuccess(true);
            rm.setMessage("Insert Successfuly");
        } catch (Exception e) {
            rm.setSuccess(false);
            rm.setMessage("Insert Failed: " + e.getMessage());
        }
        rm.setItem(list);
        return rm;
    }

    //============== new method from LUKAS 17-10-2023 ===============
    @RequestMapping(value = "/get-data")
    public @ResponseBody
    ResponseMessage copyPaste(@RequestParam String param, @RequestParam(required = true) String date, @RequestParam(required = true) String outletId) throws IOException, Exception {
        String dateCopy = date;
        if (date == null) {
            dateCopy = new SimpleDateFormat("dd-MMM-yyyy").format(Calendar.getInstance().getTime());
        }

        ResponseMessage rm = new ResponseMessage();
        try {
            rm.setItem(processServices.getDataMaster(param, dateCopy, outletId));
            rm.setSuccess(true);
            rm.setMessage("Get Data Table Successfuly " + param + " For " + dateCopy);
            System.err.println(getDateTimeForLog() + "get-data: " + outletId + ": " + param + ": " + rm.getItem().size() + "rows");
        } catch (Exception e) {
            rm.setSuccess(false);
            rm.setMessage("Get Data Table " + param + " For " + dateCopy + " Error : " + e.getMessage());
            System.err.println(getDateTimeForLog() + "get-data error: " + outletId + ": " + param + ": " + e.getMessage());
        }
        return rm;
    }

    @RequestMapping(value = "/receive-data")
    public @ResponseBody
    ResponseMessage insertDataMaster(@RequestBody Map<String, Object> param) throws IOException, Exception {
        String outletId = param.get("outletId") != null ? param.get("outletId").toString() : null;
        String tableName = param.get("tableName").toString();
        List<Map<String, Object>> bodyData = (List<Map<String, Object>>) param.get("data");
        List errors = new ArrayList();
        List response = new ArrayList();
        ResponseMessage rm = new ResponseMessage();
        rm.setItem(new ArrayList());
        try {
            if (!bodyData.isEmpty()) {
                Map<String, Object> resp = processServices.insertDataMaster(tableName, bodyData, outletId);
                System.err.println(getDateTimeForLog() + "receive-data: " + outletId + ": " + tableName + ": " + resp);
                if (resp.containsKey("errors") && resp.get("errors") instanceof List) {
                    List err = ((List) resp.get("errors"));
                    if (!err.isEmpty()) {
                        errors = err;
                    }
                }
                Integer d = 0;
                if (resp.containsKey("total") && resp.get("total") instanceof Number) {
                    int total = ((Number) resp.get("total")).intValue();
                    if (total > 0) {
                        d = total;
                    }
                }
                if (d > 0 && errors.isEmpty()) {
                    rm.setSuccess(true);
                    rm.setMessage("Insert Data for table " + tableName + " Success");
                } else if (d > 0 && !errors.isEmpty()) {
                    rm.setSuccess(true);
                    rm.setMessage("Insert Data for table " + tableName + " Success Partially");
                } else {
                    rm.setSuccess(false);
                    rm.setMessage("Insert Data for table " + tableName + " Failed");
                }
                response.add(resp);
                rm.setItem(response);
            } else {
                rm.setSuccess(true);
                rm.setMessage("No Data to Insert for table " + tableName);
            }

        } catch (Exception e) {
            System.err.println(getDateTimeForLog() + "receive-data error: " + outletId + ": " + tableName + ": " + e.getMessage());
            rm.setSuccess(false);
            rm.setMessage("Insert Data Table " + tableName + " Error : " + e.getMessage());
        }
        return rm;
    }
    //============== End method from LUKAS 17-10-2023 ===============

    @RequestMapping(value = "/execVmByOctd")
    public @ResponseBody
    ResponseMessage execVmByOctd(@RequestParam String param, @RequestParam(required = true) String date, @RequestParam(required = true) String outletId) throws IOException, Exception {
        String transDate = date;
        if (date == null) {
            transDate = new SimpleDateFormat("dd-MMM-yyyy").format(Calendar.getInstance().getTime());
        }

        ResponseMessage rm = new ResponseMessage();
        try {
            int result = processServices.execVmByOctd(outletId, transDate);
            rm.setItem(new ArrayList());
            rm.setSuccess(result > 0);
            rm.setMessage("execVmByOctd " + (result > 0 ? "Success" : "Error") + ": " + result + " rows affected.");
            System.err.println(getDateTimeForLog() + "execVmByOctd: " + outletId + ": " + (result > 0));
        } catch (Exception e) {
            System.err.println(getDateTimeForLog() + "execVmByOctd error: " + outletId + ": " + e.getMessage());
            rm.setSuccess(false);
            rm.setMessage("execVmByOctd Error: " + e.getMessage());
        }
        return rm;
    }

    @RequestMapping(value = "/check-allow-take-master")
    public @ResponseBody
    ResponseMessage checkAllowTakeMaster(@RequestBody Map<String, Object> params) throws IOException, Exception {
        ResponseMessage rm = new ResponseMessage();
        try {
            List<Map<String, Object>> result = processServices.checkAllowTakeMaster(params);
            rm.setItem(new ArrayList());
            rm.setSuccess(true);
            rm.setItem(result);
            rm.setMessage("OK");
            System.err.println(getDateTimeForLog() + "checkAllowTakeMaster: " + params.get("outletCode") + " remark: " + params.get("remark") + ": " + (result.size()));
        } catch (Exception e) {
            System.err.println(getDateTimeForLog() + "checkAllowTakeMaster error: " + ": " + e.getMessage());
            rm.setSuccess(false);
            rm.setMessage("Error: " + e.getMessage());
        }
        return rm;
    }

    @RequestMapping(value = "/update-sync-detail")
    public @ResponseBody
    ResponseMessage updateMSyncDetail(@RequestBody Map<String, Object> params) throws IOException, Exception {
        ResponseMessage rm = new ResponseMessage();
        try {
            Map<String, Object> result = processServices.updateMSyncDetail(params);
            rm.setItem(new ArrayList());
            rm.setSuccess(true);
            rm.setMessage("OK");
            System.err.println(getDateTimeForLog() + "updateMSyncDetail: " + params.get("outletCode") + " remark: " + params.get("remark") + ": " + (result.size()));
        } catch (Exception e) {
            System.err.println(getDateTimeForLog() + "updateMSyncDetail error: " + ": " + e.getMessage());
            rm.setSuccess(false);
            rm.setMessage("Error: " + e.getMessage());
        }
        return rm;
    }

    @GetMapping("/get-latest-log")
    public ResponseEntity<?> downloadLatestLogFile(@RequestParam(required = true) String userUpd) {
        try {
            if(currentDir == null){
                return new ResponseEntity<>("Log Dir is null", HttpStatus.BAD_REQUEST);
            }
            Path jarDir = Paths.get(currentDir);
            if(jarDir == null){
                return new ResponseEntity<>("Log Dir not valid", HttpStatus.BAD_REQUEST);
            }
            Optional<Path> latestLogFile = findLatestLogFile(jarDir);
            if (latestLogFile.isPresent()) {
                Path logFile = latestLogFile.get();
                InputStreamResource resource = new InputStreamResource(Files.newInputStream(logFile));

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + logFile.getFileName());
                System.out.println(getDateTimeForLog() + "downloadLatestLogFile by " + userUpd + ": " + jarDir.getParent() + "\\" + logFile.getFileName());
                return new ResponseEntity<>(resource, headers, HttpStatus.OK);
            } else {
                return new ResponseEntity<>("No log files found.", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            System.err.println(getDateTimeForLog() + "downloadLatestLogFile error: " + ": " + e.getMessage() + " :: " + Arrays.toString(e.getStackTrace()));
            return new ResponseEntity<>("Error occurred while fetching the log file.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Optional<Path> findLatestLogFile(Path directory) throws IOException {
        return Files.list(directory)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".log"))
                .max(Comparator.comparingLong(p -> {
                    try {
                        return Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toMillis();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    public String getDateTimeForLog() {
        return LocalDateTime.now().format(dateTimeFormatter) + " - ";
    }
}
