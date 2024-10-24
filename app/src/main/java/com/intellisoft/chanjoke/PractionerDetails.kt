package com.intellisoft.chanjoke

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.intellisoft.chanjoke.databinding.FragmentPractionerDetailsBinding
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.shared.Login

class PractionerDetails : Fragment() {

    private lateinit var _binding: FragmentPractionerDetailsBinding
    private val binding get() = _binding

    private val formatterClass = FormatterClass()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPractionerDetailsBinding.inflate(inflater, container, false)

        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = resources.getString(R.string.app_name)
            setDisplayHomeAsUpEnabled(true)
        }
        getUserDetails()

        binding.btnSignOut.setOnClickListener {

            formatterClass.saveSharedPref("isLoggedIn", "false", requireContext())
            val intent = Intent(requireContext(), Login::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)

        }
//        binding.bottomNavigationView.setOnNavigationItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.navigation_home -> {
//                    val intent = Intent(requireContext(), MainActivity::class.java)
//                    startActivity(intent)
//                    true
//                }
//
//                R.id.navigation_patient -> {
//                    val intent = Intent(requireContext(), MainActivity::class.java)
//                    startActivity(intent)
//                    true
//                }
//
//                R.id.navigation_vaccine -> {
//                    val intent = Intent(requireContext(), MainActivity::class.java)
//                    startActivity(intent)
//                    true
//                }
//
//                R.id.navigation_profile -> {
//                    val intent = Intent(requireContext(), MainActivity::class.java)
//                    startActivity(intent)
//                    true
//                }
//
//                else -> false
//            }
//        }


    }

    private fun getUserDetails() {

        val practitionerFullNames =
            formatterClass.getSharedPref("practitionerFullNames", requireContext())
        val practitionerIdNumber =
            formatterClass.getSharedPref("practitionerIdNumber", requireContext())
        val practitionerRole = formatterClass.getSharedPref("practitionerRole", requireContext())
        val fhirPractitionerId =
            formatterClass.getSharedPref("fhirPractitionerId", requireContext())
//        val practitionerId = formatterClass.getSharedPref("practitionerId", requireContext())
        val practitionerId = formatterClass.getSharedPref("id", requireContext())
        val practitionerEmail = formatterClass.getSharedPref("practitionerEmail", requireContext())
//        val practitionerPhone = formatterClass.getSharedPref("practitionerPhone", requireContext())
        val practitionerPhone = formatterClass.getSharedPref("phone", requireContext())
        val countyName = formatterClass.getSharedPref("countyName", requireContext())
        val subCountyName = formatterClass.getSharedPref("subCountyName", requireContext())
        val wardName = formatterClass.getSharedPref("wardName", requireContext())

        val email = "Email: $practitionerEmail"
        val phone = "Phone Number: $practitionerPhone"
        val pracId = "User ID: $practitionerId"
        val facilityName = formatterClass.getSharedPref("practitionerFacilityName", requireContext())
        binding.tvEmailAddress.text = email
        binding.tvPhoneNumber.text = phone
        binding.tvIdNumber.text = pracId
        binding.tvFullName.text = practitionerFullNames

        val county = "County: $countyName"
        val subCounty = "Sub County: $subCountyName"
        val ward = "Ward: $wardName"
        val facility = "Facility: $facilityName"

        binding.tvCounty.text = county
        binding.tvSubCounty.text = subCounty
        binding.tvWardName.text = ward

        binding.apply {
            tvFacility.text = facility
        }

    }
}