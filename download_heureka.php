<?php

$todayDate = date("Y-m-d");
define('GLOBAL_LOG_FILE', '/home/administrator/fire-heureka/test/logs/' . $todayDate . '/output.log');
define('GLOBAL_ERR_FILE', '/home/administrator/fire-heureka/test/logs/' . $todayDate . '/error.log');

date_default_timezone_set('Europe/Berlin');
session_start();
require_once 'db.php';
require 'vendor/autoload.php';

use Ramsey\Uuid\Guid\Guid;


function download_data($praxis_name, $access_token, $user_id) {
    global $todayDate;

    $ch = curl_init();

    // FILE USED TO KEEP TRACK OF TIME TAKEN TO DOWNLOAD DATA
    $logs_folder = '/home/administrator/fire-heureka/test/logs/' . $todayDate;

    if (!file_exists($logs_folder)) {
	mkdir($logs_folder, 0777, true);
    }

    $time_file = $logs_folder . '/time.log';
    if (!file_exists($time_file)) {
        touch($time_file);
    }

    file_put_contents($time_file, "Start Time: " . date("Y-m-d H:i:s") . PHP_EOL, LOCK_EX);

    $configuration = configure_heureka($user_id, $ch);
    if($configuration === null) {
	return;
    }

    $heureka_grants = $_SESSION['heurekaGrants'] ?? [];

    if (isset($heureka_grants['PATIENT']) && in_array('READ', $heureka_grants['PATIENT'])) {
        get_patients_heureka($praxis_name, $user_id, $ch);
	file_put_contents($time_file, "End Time: " . date("Y-m-d H:i:s") . PHP_EOL, FILE_APPEND | LOCK_EX);

        curl_close($ch);
        return;
    } else {
	log_message("error", "ERROR: You don't have the required permission to download the patients");
	curl_close($ch);
        return;
    }
}





function get_access_token($user_id, $ch) {
    $lock_file = "/tmp/token_refresh.lock";
    $fp = fopen($lock_file, "w");

    flock($fp, LOCK_EX);

    try {
        $conn = get_db_connection();

        $query = "SELECT user_tokens.access_token, user_tokens.token_expiry
            FROM user_credentials
            JOIN user_tokens ON user_credentials.id = user_tokens.user_id
            WHERE user_credentials.id = ?";

        if ($stmt = $conn->prepare($query)) {
            $stmt->execute([$user_id]);

            $result = $stmt->fetch(PDO::FETCH_ASSOC);

            if ($result) {
                $access_token = $result['access_token'];
                $token_expiry = $result['token_expiry'];
                $current_time = new DateTime();
                $token_expiry_time = new DateTime($token_expiry);

		// IF TOKEN MISSING OR EXPIRED, REQUEST A NEW ONE
                if (!$access_token || $current_time > $token_expiry_time) {
                    $access_token = get_new_access_token($user_id, $ch);

		    if ($access_token === null) {
		        return null;
		    }
                }

                return $access_token;
            } else {
                return null;
            }
        }
    } catch (Exception $e) {
	log_message("error", "ERROR: Exception in get_access_token - " . $e->getMessage());
        return null;
    } finally {
	flock($fp, LOCK_UN);
        fclose($fp);
    }
}




function get_new_access_token($user_id, $other_ch) {

    $conn = get_db_connection();

    $query = "
        SELECT user_tokens.refresh_token
        FROM user_credentials
        JOIN user_tokens ON user_credentials.id = user_tokens.user_id
        WHERE user_credentials.id = ?
    ";

    if ($stmt = $conn->prepare($query)) {
        $stmt->execute([$user_id]);
        $result = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($result && isset($result['refresh_token'])) {
            $refresh_token = $result['refresh_token'];
        }

        if (!$refresh_token) {
            log_message("error", "ERROR: No refresh token in database");
	    return null;
        }

        $data = [
            "grant_type" => "refresh_token",
            "refresh_token" => $refresh_token,
            "client_id" => "173e5603-6107-4521-a465-5b9dc86b2e95",
	    //"client_id" => "f49bcad4-cf7b-4fd8-8b4d-aaab9b390cfb",
        ];


	$ch = curl_init();

	$proxies = [
            'https' => 'http://tunnel.testing.heureka.health:7000'
            //'https' => 'http://tunnel.heureka.health:7000'
        ];

        //curl_setopt($ch, CURLOPT_URL, "https://token.heureka.health/oauth2/token");
	curl_setopt($ch, CURLOPT_URL, "https://token.testing.heureka.health/oauth2/token");
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
        curl_setopt($ch, CURLOPT_POST, 1);
        curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query($data));
        //curl_setopt($ch, CURLOPT_SSLCERT, __DIR__ . "/resources/fire.crt");
        //curl_setopt($ch, CURLOPT_SSLKEY, __DIR__ . "/resources/fire.key");
	curl_setopt($ch, CURLOPT_SSLCERT, __DIR__ . "/old_cert/fire.crt");
	curl_setopt($ch, CURLOPT_SSLKEY, __DIR__ . "/old_cert/fire.key");
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            "Content-Type: application/x-www-form-urlencoded"
        ]);
	//curl_setopt($ch, CURLOPT_VERBOSE, true);

	$response = curl_exec($ch);

        if (curl_errno($ch)) {
            log_message("error", "CURL ERROR when requesting new access token: " . curl_error($ch));
	    return null;
        }

        $response_data = json_decode($response, true);

        if (isset($response_data['access_token']) && isset($response_data['refresh_token'])) {
            log_token_request($user_id, $response_data);
	    save_token($response_data['access_token'], $response_data['refresh_token'], $response_data['expires_in'], $user_id, 'update');
	    curl_close($ch);
	    return $response_data['access_token'];
        } else {
	    log_message("error", "ERROR: No access or refresh token in response");
            return null;
        }
    } else {
	log_message("error", "ERROR: Error preparing the query " . $query->error);
        return null;
    }
}


function log_token_request($user_id, $response_data) {
    $todayDate = date("Y-m-d");
    $log_file = "/home/administrator/fire-heureka/test/logs/" . $todayDate . "/token.log";

    if (!file_exists($log_file)) {
        file_put_contents($log_file, "=== Token Request Log ===\n", LOCK_EX);
    }

    $log_entry = sprintf(
        "\n\n[%s] \n[USER_ID]: %s \n[Access_Token]: %s \n[Refresh_Token]: %s \n[Expiry]: %s\n",
        date("Y-m-d H:i:s"),
        $user_id,
        $response_data['access_token'] ?? 'N/A',
        $response_data['refresh_token'] ?? 'N/A',
        $response_data['expires_in'] ?? 'N/A'
    );

    file_put_contents($log_file, $log_entry, FILE_APPEND | LOCK_EX);
}


function log_message($file, $message) {
    if ($file == 'output') {
        $log_file = GLOBAL_LOG_FILE;
    } else if ($file == 'error') {
	$log_file = GLOBAL_ERR_FILE;
    }

    if (!is_dir(dirname($log_file))) {
        mkdir(dirname($log_file), 0777, true);
    }

    $log_entry = "[" . date("Y-m-d H:i:s") . "] " . $message . PHP_EOL;

    file_put_contents($log_file, $log_entry, FILE_APPEND | LOCK_EX);
}




function configure_heureka($user_id, $ch) {
    $user_token = get_access_token($user_id, $ch);

    if ($user_token === null) {
	log_message("error", "ERROR: Failed to update token, most likely the refresh token is invalid");
	return null;
    }

    $configuration_url = "https://api.testing.heureka.health/api-configuration";
    //$configuration_url = "https://api.heureka.health/api-configuration";

    curl_setopt($ch, CURLOPT_URL, $configuration_url);
    curl_setopt($ch, CURLOPT_POST, 0);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        "Authorization: Bearer $user_token"
    ]);
    //curl_setopt($ch, CURLOPT_SSLCERT, __DIR__ .  "/resources/fire.crt");
    //curl_setopt($ch, CURLOPT_SSLKEY, __DIR__ .  "/resources/fire.key");
    curl_setopt($ch, CURLOPT_SSLCERT, __DIR__ . "/old_cert/fire.crt");
    curl_setopt($ch, CURLOPT_SSLKEY, __DIR__ . "/old_cert/fire.key");

    $response = curl_exec($ch);

    if (curl_errno($ch)) {
	$error_message = curl_error($ch);
        log_message("error", "CURL ERROR: " . $error_message);
	return null;
    }

    $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);

    if ($http_code == 200) {
        $response_json = json_decode($response, true);

	// SAVE IMPORTANT CONFIGURATIONS, AS THEY ARE NEEDED FURTHER IN THE CODE
        $_SESSION['fhirEndpoint'] = $response_json['fhirEndpoint'] ?? null;
        $_SESSION['heurekaProxy'] = $response_json['proxy'] ?? null;
        $_SESSION['healthcareProviderId'] = $response_json['healthcareProviderId'] ?? null;
        $_SESSION['heurekaGrants'] = $response_json['grants'] ?? null;

        return json_encode(["configuration" => $response_json]);
    } else {
	log_message("error", "ERROR: Failed to retrieve patient data, Error code: " . $http_code . ", Response: " . $response);
        return null;
    }
}




function get_patients_heureka($praxis_name, $user_id, $ch) {

    $todayDate = date("Y-m-d");
    $baseDir = '/home/administrator/fire-heureka/test/full_download/';
    $folderPath = $baseDir . $todayDate;
    $filePath = $folderPath . '/' . $praxis_name . '.json';

    if (!is_dir($folderPath)) {
        mkdir($folderPath, 0777, true);
    }

    $fileObj = fopen($filePath, 'w');

    if ($fileObj === false) {
       log_message("error", "Error: Unable to open the file for writing");
       return;
    }

    fwrite($fileObj, '{"resourceType" : "Bundle", "entry": [');

    $cert = [
        //"cert" => __DIR__ . "/resources/fire.crt",
        //"key"  => __DIR__ . "/resources/fire.key"
        "cert" => __DIR__ . "/old_cert/fire.crt",
        "key" => __DIR__ . "/old_cert/fire.key"
    ];
    $ca_cert = __DIR__ . '/old_cert/heureka-testing.pem';
    //$ca_cert = __DIR__ . '/resources/heureka-production.pem';
    $proxies = [
        'https' => 'http://tunnel.testing.heureka.health:7000'
        //'https' => 'http://tunnel.heureka.health:7000'
    ];

    $uuid_v4 = Guid::uuid4()->toString();
    $context_type = "PATIENT_EXPORT";
    $heureka_role = $_SESSION['heureka_role'] ?? 'SYSTEM';


    curl_setopt($ch, CURLOPT_HTTPHEADER, []);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_SSLCERT, $cert['cert']);
    curl_setopt($ch, CURLOPT_SSLKEY, $cert['key']);
    curl_setopt($ch, CURLOPT_CAINFO, $ca_cert);
    curl_setopt($ch, CURLOPT_PROXY, $proxies['https']);


    $numProcesses = 3;
    $processes = [];

    $hasNextFile = 'has_next.txt';
    $has_next = true;
    file_put_contents($hasNextFile, $has_next ? 'true' : 'false');
    $start_offset = 0;
    $offset = 300;

    while ($has_next) {
	$has_next_value = trim(file_get_contents($hasNextFile));
        if ($has_next_value == 'false') {
	    $has_next = false;
            break;
        }

	$user_token = get_access_token($user_id, $ch);
	if ($user_token === null) {
	    log_message("error", "ERROR: Failed to update token, most likely the refresh token is invalid");
	    return;
	}


	curl_setopt($ch, CURLOPT_HTTPHEADER, [
            "Authorization: Bearer $user_token",
            "X-HEUREKA-RequestContextId: $uuid_v4",
            "X-HEUREKA-RequestContextType: $context_type",
            "X-HEUREKA-UserRole: $heureka_role"
        ]);

        for ($i = 0; $i < $numProcesses; $i++) {
            $pid = pcntl_fork();

            if ($pid == -1) {
                die("Could not fork process $i\n");
            } elseif ($pid === 0) {
                $fhir_endpoint = $_SESSION['fhirEndpoint'] ?? null;

                if (!$fhir_endpoint) {
                    log_message("error", "ERROR: FHIR endpoint not available");
                    return;
		}

		$your_offset = $start_offset + ($offset * $i);
                $url = $fhir_endpoint . '/Patient?_count=300&_offset=' . $your_offset;
                log_message("output", $url);

                curl_setopt($ch, CURLOPT_URL, $url);

                $response = curl_exec($ch);
                $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);

                if (curl_errno($ch)) {
		    log_message("error", "CURL ERROR: " . curl_error($ch));
                    return;
                }

                if ($http_code == 200) {
	            $bundle = json_decode($response, true);
                    $total_patients = $bundle['total'];
                    log_message("output", "Patient to process: $total_patients\n");

		    if ($total_patients > 0) {
                        if ($total_patients < 299) {
                            $has_next = false;
			    file_put_contents($hasNextFile, 'false');
                        }

                        if (isset($bundle['entry'])) {
                            $entries = $bundle['entry'];

			    $baseDirTemp = '/home/administrator/fire-heureka/test/full_download/' . $todayDate;
                            $filePathTemp = $baseDirTemp . '/temp' . $i . '.json';

                            $fileObjTemp = fopen($filePathTemp, 'w');

                            foreach ($entries as $i => $patient) {
                                log_message("output", "Patient [" . $your_offset+$i . "]\n");

                                fwrite($fileObjTemp, json_encode($patient));

                                $elements_patient = get_elements_for_patient($patient['resource']['id'], $user_id, $uuid_v4, $context_type, $heureka_role, $ch);

				if ($elements_patient !== null) {
				    if ($elements_patient !== '') {
                                        fwrite($fileObjTemp, ",");
                                        fwrite($fileObjTemp, $elements_patient);
                                    }

                                    if ($i < count($entries) - 1 || $total_patients === 299 || $total_patients === 300) {
                                        fwrite($fileObjTemp, ",");
                                    }
				}
                            }

                        } else {
			    log_message("error", "ERROR: No patients found in response");
                            return;
                        }
		    }
                } else {
                    log_message("error", "Request failed, " . $http_code . ", " . $response);
                    return;
                }

                return;
            } else {
	        $processes[] = $pid;
	    }
        }


        foreach ($processes as $process) {
            pcntl_waitpid($process, $status);
        }

        for ($i = 0; $i < $numProcesses; $i++) {
            $tempFile = "full_download/$todayDate/temp$i.json";
	    if (file_exists($tempFile)) {
                $tempContent = file_get_contents($tempFile);
                fwrite($fileObj, $tempContent);
                unlink($tempFile);
            }
        }

        $start_offset += $offset * $numProcesses;
    }

    fwrite($fileObj, ']}');
    fclose($fileObj);
    unlink($hasNextFile);

    return;
}


$MAX_RETRIES = 5;
$MAX_SLEEP_TIME = 500000;
$MIN_SLEEP_TIME = 10000;
$DEFAULT_SLEEP_TIME = 50000;
$SUCCESS_THRESHOLD = 10;


$sleep_time = $DEFAULT_SLEEP_TIME;
$fast_responses = 0;

function get_elements_for_patient($patient_id, $user_id, $uuid_v4, $context_type, $heureka_role, $ch) {
    global $MAX_RETRIES, $MAX_SLEEP_TIME, $MIN_SLEEP_TIME, $SUCCESS_THRESHOLD, $sleep_time, $fast_responses;
    $retry = 0;
    $successful_request = false;

  while($retry < $MAX_RETRIES && !$successful_request) {
    $user_token = get_access_token($user_id, $ch);
    if ($user_token === null) {
	return null;
    }

    $url_suffixes = [
        "Observation"               => ["/Observation?patient=Patient/", $_SESSION['heurekaGrants']['OBSERVATION']],
        "Condition"                 => ["/Condition?patient=Patient/", $_SESSION['heurekaGrants']['CONDITION']],
        "MedicationStatement"       => ["/MedicationStatement?subject=Patient/", $_SESSION['heurekaGrants']['MEDICATION_STATEMENT']]
    ];

    $patient_info = "";

    $multiHandle = curl_multi_init();
    $curlHandles = [];
    $responses = [];

    $cert = [__DIR__ . '/old_cert/fire.crt', __DIR__ . '/old_cert/fire.key'];
    //$ca_cert = __DIR__ . '/resources/heureka-production.pem';
    $ca_cert = __DIR__ . '/old_cert/heureka-testing.pem';
    $proxies = [
        'https' => 'http://tunnel.testing.heureka.health:7000'
        //'https' => 'http://tunnel.heureka.health:7000'
    ];

    foreach ($url_suffixes as $key => $suffix_data) {
        $url_suffix = $suffix_data[0];
        $grants = $suffix_data[1];

        if (in_array('READ', $grants)) {
            $url = $_SESSION['fhirEndpoint'] . $url_suffix . $patient_id;

            $headers = [
                "Authorization: Bearer $user_token",
                "X-HEUREKA-RequestContextId: $uuid_v4",
                "X-HEUREKA-RequestContextType: $context_type",
                "X-HEUREKA-UserRole: $heureka_role"
            ];

	    $new_ch = curl_init();

            $options = [
                CURLOPT_URL => $url,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_SSL_VERIFYPEER => true,
                CURLOPT_SSLCERT => $cert[0],
                CURLOPT_SSLKEY => $cert[1],
                CURLOPT_CAINFO => $ca_cert,
                CURLOPT_PROXY => $proxies['https'],
                CURLOPT_HTTPHEADER => $headers,
                //CURLOPT_VERBOSE => true
            ];

            curl_setopt_array($new_ch, $options);

            curl_multi_add_handle($multiHandle, $new_ch);
            $curlHandles[$key] = $new_ch;
        }
    }

    do {
        $status = curl_multi_exec($multiHandle, $active);
        curl_multi_select($multiHandle);
    } while ($active && $status == CURLM_OK);

    $count_success = 0;

    foreach ($curlHandles as $key => $temp_ch) {
        $responses[$key] = curl_multi_getcontent($temp_ch);
	$http_code = curl_getinfo($temp_ch, CURLINFO_HTTP_CODE);

    	if ($http_code === 200) {
	    $count_success++;
	    $fast_responses++;
	} else if ($http_code === 400) {
            log_message("error", "ERROR 400: " . $responses[$key]);
    	} else if ($http_code === 403) {
	    //retry++;
	} else if ($http_code === 429) {
	    // IF DOWNLOADING TOO FAST, SLOW DOWN THE PROCESS
	    $count_success++;
	    $sleep_time = min($sleep_time * 2, $MAX_SLEEP_TIME);
	    $fast_responses = 0;
	} else if ($http_code === 500) {
	    //ASSUMING IT MEANS NO DATA
	    $count_success++;
	    $fast_responses++;
	}

        curl_multi_remove_handle($multiHandle, $temp_ch);
        curl_close($temp_ch);
    }

    $retry++;

    // IF MANY REQUEST IN A ROW NOT TOO FAST, TRY MAKING IT SLIGHTLY FASTER
    if ($fast_responses > $SUCCESS_THRESHOLD) {
	$sleep_time = max($sleep_time / 2, $MIN_SLEEP_TIME);
	$fast_responses = 0;
    }


    // IF ALL REQUEST WERE SUCCESSFUL, MOVE ON
    if ($count_success === count($curlHandles)) {
	$successful_request = true;
    }

    curl_multi_close($multiHandle);
  }

    if ($successful_request) {
        foreach (["Observation", "Condition", "MedicationStatement"] as $endpoint) {
            if (isset($responses[$endpoint])) {
                $response_data = json_decode($responses[$endpoint], true);
                if (isset($response_data['entry']) && json_last_error() == JSON_ERROR_NONE) {
                    $patient_info .= json_encode($response_data['entry']) . ",";
                }
            }
        }

        usleep($sleep_time);

        $patient_info = rtrim($patient_info, ",");
        return json_encode(["patient_data" => $patient_info]);
    } else {
	log_message("error", "ERROR: Too many errors in request, stopping");
	return null;
    }
}


$conn = get_db_connection();

$sql = "
    SELECT 
        uc.username AS praxis_name,   
        ut.user_id,               
        ut.access_token           
    FROM 
        user_credentials uc
    JOIN 
        user_tokens ut
    ON 
        uc.id = ut.user_id;
";

$stmt = $conn->prepare($sql);
$stmt->execute();

$results = $stmt->fetchAll(PDO::FETCH_ASSOC);

$to_download = [2];

foreach ($results as $row) {
    if (empty($to_download) || in_array($row['user_id'], $to_download)) {
        $pid = pcntl_fork();

        if ($pid == -1) {
            die("Failed");
        } else if ($pid) {
            continue;
        } else {
            //print_r($row);
            download_data($row['praxis_name'], $row['access_token'], $row['user_id']);
            exit();
        }

    }
}

while (pcntl_waitpid(0, $status) != -1);
